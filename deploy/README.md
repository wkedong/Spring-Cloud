# deploy/ —— 容器化交付物

本目录把教学仓库打包成可直接运行的容器编排，共三件套（外加一份 Nacos 可选编排）：

| 文件 | 作用 |
| --- | --- |
| `Dockerfile` | 通用多阶段镜像：Maven 构建 → `jarmode=layertools` 分层 → JRE 运行，非 root |
| `docker-compose.yml` | 整栈编排：eureka/config/producer×2/feign/ribbon/hystrix/gateway + mysql + zipkin |
| `docker-compose-nacos.yml` | 可选：**Nacos 3.x** 单机版（8848 + gRPC 9848/9849，控制台独立端口 8080）与 nacos-demo（8210/8211），对应 docs/24 |
| `README.md` | 本文档 |
| `prometheus.yml`、`grafana/` | 与容器化无关的监控配置（Prometheus 抓取 + Grafana 数据源/看板），归 docs/27 |

> ⚠️ **本机没有 Docker daemon**（`docker` CLI 不存在，只有 Homebrew 的 `docker-compose` 客户端）。
> 镜像**没有真正构建过**，compose **也没有真正 up 起来过**。已完成的是：
> ① `docker-compose -f deploy/docker-compose.yml config` 静态校验通过（exit 0，无告警）；
> ② 镜像里那条关键命令 `java -Djarmode=layertools ... list/extract` 在本机用 JDK 17 实跑过（见下）。
> 未核实之处集中列在最后一节，请不要把本文档当作「已验证可运行」的证明。

## 一、构建

构建上下文必须是**仓库根目录**：多模块 Maven 工程在 `-pl <module> -am` 时仍要读到根 `pom.xml`
与全部子模块 `pom.xml`，而 `deploy/` 里没有这些文件。

```bash
cd <仓库根目录>
docker build -f deploy/Dockerfile --build-arg MODULE=service-producer --build-arg APP_PORT=6070 \
             -t spring-cloud/service-producer:0.0.1 .
```

`MODULE` 取根 `pom.xml` 的 `<modules>` 里的目录名（如 `eureka`、`config`、`service-producer`、
`service-consumer-feign`、`zuul`），`APP_PORT` 只影响 `EXPOSE` 与默认健康检查路径。

**建议先加一个 `.dockerignore`**（本仓库目前没有）：`COPY . .` 会把每个模块的 `target/`
和 `.git/` 一起送进构建上下文，几 GB 的上下文会让构建明显变慢。

## 二、运行

```bash
cd <仓库根目录>
docker compose -f deploy/docker-compose.yml build
docker compose -f deploy/docker-compose.yml up -d
docker compose -f deploy/docker-compose.yml ps          # 等 healthcheck 变成 healthy

curl http://localhost:6060/                              # Eureka 控制台，应看到各服务
curl http://localhost:6010/service-producer/dev/develop  # 配置中心返回 name=test-dev-develop
curl http://localhost:6070/testGet                       # producer 直连
curl http://localhost:7020/testFeign                     # Feign 调用
curl http://localhost:7040/testHystrix                   # 约 3 秒后返回兜底文案
curl http://localhost:6050/api/producer/testGet          # 网关前缀路由
# Zipkin UI: http://localhost:9411
```

启动顺序由 `depends_on` + `healthcheck` 保证：`mysql → eureka → config → producer → 消费者`。
这与 `docs/09-本地运行指南.md` 的裸机顺序一致，也是这个仓库最容易踩的坑：
配置中心没起，业务服务会在启动阶段反复重试拉配置。

## 三、镜像分层（为什么值得分层）

`spring-boot-maven-plugin` 在 Boot 2.3+ 默认开启分层。用 JDK 17 在本机对
`service-producer-0.0.1-SNAPSHOT.jar` 实测：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
$JAVA_HOME/bin/java -Djarmode=layertools -jar service-producer/target/service-producer-0.0.1-SNAPSHOT.jar list
# dependencies
# spring-boot-loader
# snapshot-dependencies
# application

$JAVA_HOME/bin/java -Djarmode=layertools -jar service-producer/target/service-producer-0.0.1-SNAPSHOT.jar \
    extract --destination /tmp/layers-demo
du -sh /tmp/layers-demo/*
#  96K  application              （20 个文件：BOOT-INF/classes（含 application.yml 等本地资源）、META-INF、layers.idx）
#  69M  dependencies             （151 个文件：BOOT-INF/lib 里的第三方 jar，最大的一层）
#   0B  snapshot-dependencies    （0 个文件：本仓库没有 SNAPSHOT 外部依赖，目录是空的）
# 408K  spring-boot-loader       （68 个文件：org/springframework/boot/loader 启动器）
```

> 这组 `du` 是 **Boot 2.7 时代的分层实测存档**：层名与「谁大谁小」的结论不变，具体文件数/体积随依赖变化。
> 其中 `application` 层原先列的是 `bootstrap.yml`——Boot 4 的模块已经没有这个文件（配置在 `application.yml`），已按现状更正。
> 想拿最新数字，把上面三条命令重跑一次即可。

`Dockerfile` 就是按这个顺序 `COPY --from=builder` 的。收益很直接：

- 改一行业务代码 → 只有 `application` 层（96K）失效，重新构建只需重传这一层；
- 升级一个第三方库 → 只有 `dependencies` 层（69M）失效；
- 追加快照依赖 → 只有 `snapshot-dependencies` 层失效。

> 反例：把 fat jar 整包 `COPY` 进镜像，改一行代码就要重传 70MB。
> `snapshot-dependencies` 层虽然是空目录，但**目录必须存在**，否则 `COPY` 会失败——
> 所以 `Dockerfile` 里这一行不能删。

## 四、常见问题

**1. 时区**

容器默认 UTC，日志时间会比本地少 8 小时、和 Zipkin 的时间轴对不上。三处都要照顾到：

- 镜像里 `ENV TZ=Asia/Shanghai` 并装了 `tzdata`；
- `JAVA_OPTS` 带 `-Duser.timezone=Asia/Shanghai`；
- MySQL 连接的 JDBC URL 带 `serverTimezone=Asia/Shanghai`（仓库里的 URL 已经带上了）。

**2. 内存**

JVM 不设堆上限时按**宿主机**内存算，容器里容易被 OOMKilled。镜像默认给了
`-XX:MaxRAMPercentage=75.0`（JDK 11+ 的容器感知默认开启），配合 compose 的 `mem_limit`/`deploy.resources.limits`
即可。要覆盖就传 `-e JAVA_OPTS="-Xmx512m ..."`。

**3. 健康检查**

- 有 actuator 的模块：`curl -fsS http://127.0.0.1:<port>/actuator/health`；
- **`config` 模块没有引 actuator**，探针换成配置中心自带的 REST 接口
  `curl -fsS http://127.0.0.1:6010/service-producer/dev/develop`——它会真的查一次 MySQL，
  比探端口更能说明「配置中心可用」；
- `zipkin` 不设 healthcheck：官方镜像里可用的 HTTP 客户端不确定，写错会一直显示 unhealthy；
  它又是「上报型」依赖，掉了只丢 span，因此没有服务 `depends_on` 它；
- `start-period` 给了 40~60s：Spring Cloud 应用要注册、拉配置，起得比 Web 应用慢。

**4. 配置外置与 profile 传递**

仓库里的 `application.yml` 写的是 `localhost:6060` / `localhost:6010`（Boot 4 已没有 `bootstrap.yml`，
配置中心改用 `spring.config.import: optional:configserver:http://localhost:6010/` 接入），容器里 `localhost`
指向容器自己，必须用环境变量覆盖（Spring Boot relaxed binding）：

| 环境变量 | 覆盖的配置项 | compose 里的值 |
| --- | --- | --- |
| `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` | `eureka.client.serviceUrl.defaultZone` | `http://eureka:6060/eureka/` |
| `SPRING_CLOUD_CONFIG_URI` | `spring.cloud.config.uri` | `http://config:6010/` |
| `MANAGEMENT_ZIPKIN_TRACING_ENDPOINT` | `management.zipkin.tracing.endpoint` | `http://zipkin:9411/api/v2/spans` |
| `SPRING_DATASOURCE_URL` | `spring.datasource.url` | `jdbc:mysql://mysql:3306/...` |

> 旧键提示：Sleuth 时代的 `SPRING_ZIPKIN_BASE_URL` / `SPRING_ZIPKIN_BASEURL`（对应 `spring.zipkin.base-url`）
> 随 Sleuth 一起失效了——写了也不会报错，只是不再生效，span 不会上报到 Zipkin。

Spring profile 用 `SPRING_PROFILES_ACTIVE` 传：`producer-peer1` 传 `peer1`（端口 6070）、
`producer-peer2` 传 `peer2`（端口 6080）。**不能在 profile 特定文件里声明 `spring.profiles.active`**
（Boot 2.4+ 会直接启动失败），只能从外部传——仓库里的 `application-peer*.yml` 注释也写了这一点。
别把它和 compose 自己的 `profiles:` 搞混：后者决定「哪些服务被启动」。

环境变量到底怎么写才绑得上？本机用 Spring Boot 的 `ConfigurationPropertySources` 实测过
（方法见 `docs/29-容器化部署.md`）：

| 目标属性 | 可用的环境变量 | 结论 |
| --- | --- | --- |
| `management.zipkin.tracing.endpoint` | `MANAGEMENT_ZIPKIN_TRACING_ENDPOINT` | 现役键（Boot 4 的 Micrometer Tracing）；旧 `SPRING_ZIPKIN_BASE_URL` 已失效 |
| `spring.cloud.nacos.discovery.server-addr` | `..._SERVER_ADDR` / `..._SERVERADDR` | 两种都绑得上 |
| `spring.cloud.nacos.discovery.metadata.instance-port` | `..._INSTANCE_PORT` | ❌ 会绑成 `metadata.instance.port` |
| 同上（Map 里的 key） | `..._INSTANCE-PORT`，或命令行 `--spring.cloud.nacos.discovery.metadata.instance-port=8211` | ✅ 推荐后者 |

一句话：**普通配置项**点换下划线即可；**Map 里的 key 带连字符**时环境变量写法会产生歧义，
所以 `deploy/docker-compose-nacos.yml` 里那一项改用命令行参数。

**5. 配置中心用 MySQL 时：Flyway 要不要关**

- **全新库（推荐）**：保持 `CONFIG_FLYWAY_ENABLED=true`（默认），Flyway 会建 `properties` 表并播种数据；
- **库里已有 `properties` 表、却没有配套的 `flyway_schema_history`**（挂老库、反复初始化同一个卷）：
  启动会失败并报

  ```
  Duplicate entry '1' for key 'properties.PRIMARY'
  ```

  原因：`config/src/main/resources/schema/V1.1__Update_version.sql` 用的是**显式主键 INSERT**，
  重跑必然撞主键。这时把开关关掉：

  ```bash
  docker compose -f deploy/docker-compose.yml up -d -e ...   # 或在 compose 里改
  CONFIG_FLYWAY_ENABLED=false
  ```

  本机教学环境就是用 `java -jar ... --spring.flyway.enabled=false` 绕过的，实测记录在
  `docs/21-配置刷新与链路追踪进阶.md`。

- 顺带一提：Flyway 只播种了 `service-producer` 与 `service-consumer` 两个应用的 `name` 行
  （见 `V1.1__Update_version.sql`）。`zuul` / `service-consumer-feign` / `-ribbon` / `-hystrix`
  在全新库里查不到自己的配置，配置中心会返回 404，客户端默认 `spring.cloud.config.fail-fast=false`，
  只打印告警并继续用本地默认值启动。要让这几个服务也能读到 `name`，按 `docs/02-配置中心.md`
  往表里补行即可，例如：

  ```sql
  INSERT INTO spring_cloud_config.properties (`key`,`value`,`application`,`profile`,`label`)
  VALUES ('name','test-dev-develop','zuul','dev','develop');
  ```

**6. 端口冲突**

compose 的宿主端口与容器端口一一对应（6060/6010/6050/6070/6080/7020/7030/7040/3306/9411）。
如果你已经用 `java -jar` 在本机起过同一批服务，这些端口会被占用，`up` 会报
`port is already allocated`——两套环境二选一。确实要并存时，把映射改成 `"16070:6070"`。

## 五、未能核实之处（如实列出）

1. `docker build` / `docker compose up` **一次都没有真正执行**：本机没有 Docker daemon，
   只是用 `docker-compose config` 做了静态语法与结构校验（exit 0，无告警）。
2. 基础镜像标签的可用性未验证：`maven:3.9-eclipse-temurin-17`、`eclipse-temurin:17-jre`
   是按惯例选的，未实际拉取；`eclipse-temurin:17-jre` 默认是基于 Ubuntu 的变体，
   所以 Dockerfile 里用了 `apt-get` / `groupadd` / `useradd`——若换成 `-alpine` 变体，
   这三处都要改成 `apk` / `addgroup` / `adduser`。
3. `openzipkin/zipkin:3` 里是否带 busybox `wget` 未验证，所以**故意没给它写 healthcheck**。
4. 各服务在容器里的**实际注册与调用链**未验证：环境变量覆盖 `application.yml` 的思路是标准做法，
   但配置中心接入已从 `bootstrap.yml` + `spring-cloud-starter-bootstrap`（`spring.cloud.config.uri` 属于
   bootstrap 阶段）换成 Config Data API：`spring.config.import: optional:configserver:http://localhost:6010/`
   （Spring Cloud 2025.1 不再加载 `bootstrap.yml`，这个 starter 已废弃）。
   **这里有个尚未在容器里验证的疑点**：模块的 `application.yml` 已经把 URI 写死在 import 字符串里，
   而按 Spring Cloud Config 的解析规则，import 里带了 URI 之后 `spring.cloud.config.uri`
   （即 compose 里传的 `SPRING_CLOUD_CONFIG_URI`）只作为「import 没写 URI」时的默认值；
   容器里更稳妥的覆盖方式是 `SPRING_CONFIG_IMPORT=optional:configserver:http://config:6010/`。
   首次真实构建时请重点确认配置中心确实被访问到（这一步没有任何容器内证据）。
5. `service-consumer`(7010)、`springboot-basics`(8010)、`nacos-demo`(8210)、`sentinel-demo`(8220)、
   `stream-demo`(8230)、`security-demo`(8240)、`seata-demo`(8250/8260)、`seata-server`(8091) **没有写进编排**，
   它们要么单机自足、要么需要额外中间件（Kafka 9092、Seata 8091、Sentinel Dashboard）；
   同理，`deploy/docker-compose.yml` 里也没有 Nacos 3.x（单独走 `docker-compose-nacos.yml`）。
6. `deploy/Dockerfile` 的 `ENTRYPOINT` **还是 Boot 3.2 之前的旧启动类包名**
   `org.springframework.boot.loader.JarLauncher`；Boot 4 里只剩
   `org.springframework.boot.loader.launch.JarLauncher`（旧包已删除），
   以及文件头部注释里的「本仓库 `java.version=1.8`、只用 `javax.*`」也已过期（现在是 Java 17 / `jakarta.*`）。
   这两处**尚未修改**，本文提到的镜像构建若要真跑，请先按 `docs/29-容器化部署.md` 的写法改掉。

---
---

# 附：指标监控栈（Prometheus + Grafana）部署与验证

> 上面「容器化交付物」章节与本节是两件独立的事：那节讲怎么把仓库跑进容器，
> 本节讲怎么把**指标**采出来看。配套文档：[docs/27-指标监控与Grafana.md](../docs/27-指标监控与Grafana.md)、
> [docs/28-日志聚合.md](../docs/28-日志聚合.md)。
> 本节命令全部在 macOS（**无 Docker daemon**）上实测通过；容器命令只给出写法，未在本机执行。

## A. 本节相关文件

```
deploy/
├── prometheus.yml                                   # 抓取配置：所有模块的 /actuator/prometheus
└── grafana/
    ├── provisioning/
    │   ├── datasources/prometheus.yml               # 数据源（uid=prometheus）
    │   └── dashboards/dashboard.yml                 # 仪表盘目录扫描规则
    └── dashboards/
        └── spring-cloud-micrometer-overview.json    # 14 个面板：JVM/HTTP/缓存/连接池
```

## B. 前置条件：应用要先「肯暴露指标」

两条缺一不可，否则 `/actuator/prometheus` 直接 404：

1. pom 里有 `io.micrometer:micrometer-registry-prometheus`（本仓库 10 个模块已加）；
2. `management.endpoints.web.exposure.include` 里包含 `prometheus`
   （`springboot-basics/src/main/resources/application.yml` 已加，并配了
   `management.metrics.tags.application` 全局标签与 HTTP 直方图/SLO 桶）。

改完必须**重新打包并重启**进程才生效——加依赖不会影响已经在跑的 JVM。

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
mvn -q -pl springboot-basics -am package -DskipTests
java -jar springboot-basics/target/springboot-basics-0.0.1-SNAPSHOT.jar --server.port=18010 &
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:18010/actuator/prometheus   # 200
```

## C. 启动 Prometheus

改完配置先校验，再重启：

```bash
promtool check config deploy/prometheus.yml
# Checking deploy/prometheus.yml
#  SUCCESS: deploy/prometheus.yml is valid prometheus config file syntax
```

**本机二进制（已验证）**——共享基础设施已占用 9090，教学实例换 19090：

```bash
mkdir -p /tmp/sc-prom-data
prometheus --config.file="$PWD/deploy/prometheus.yml" \
           --storage.tsdb.path=/tmp/sc-prom-data \
           --web.listen-address=127.0.0.1:19090 \
           --storage.tsdb.retention.time=15d &
curl -s http://127.0.0.1:19090/-/ready        # Prometheus Server is Ready.
```

**容器（未在本机执行）**：

```bash
docker run -d --name prometheus -p 19090:9090 \
  -v "$PWD/deploy/prometheus.yml:/etc/prometheus/prometheus.yml:ro" \
  prom/prometheus:v3.15.0 --config.file=/etc/prometheus/prometheus.yml
```

容器里 `127.0.0.1` 指容器自己，采集宿主机上的 Spring Boot 要改成 `host.docker.internal:18010`
（macOS/Windows）或宿主机网卡 IP（Linux）。

## D. 确认 target 是 UP

```bash
curl -s http://127.0.0.1:19090/api/v1/targets | python3 -m json.tool | head -40
curl -s 'http://127.0.0.1:19090/api/v1/query?query=up' | python3 -m json.tool
curl -s --data-urlencode 'query=count(up == 1)' http://127.0.0.1:19090/api/v1/query
```

浏览器 `http://127.0.0.1:19090/targets` 看到的是同一份数据。**`up=0` 不等于服务挂了**，看 `lastError`：

| lastError | 含义 | 处理 |
| --- | --- | --- |
| `connection refused` | 端口没人监听（没启动 / 端口写错） | 确认进程与端口 |
| `server returned HTTP status 404` | 服务活着但没暴露端点 | 检查依赖与 `exposure.include`，重启 |
| `context deadline exceeded` | 端点太慢（指标太多或线程打满） | 调 `scrape_timeout`、减少指标 |

本机实测 14 个 target 中 4 个 UP（`springboot-basics` / `service-consumer-feign` /
`zuul-gateway` / `prometheus-self`），其余为 404 或 refused —— 都是「模块在跑但旧 jar 没带
prometheus 端点」或「模块没启动」，不是配置写错。

## E. 启动 Grafana

**本机二进制（已验证）**：

```bash
export SC_DASHBOARDS_PATH="$PWD/deploy/grafana/dashboards"     # dashboard.yml 里引用它
grafana server \
  --homepath /opt/homebrew/opt/grafana/share/grafana --packaging=brew \
  cfg:default.paths.provisioning="$PWD/deploy/grafana/provisioning" \
  cfg:default.paths.data=/tmp/sc-grafana/data \
  cfg:default.paths.logs=/tmp/sc-grafana/logs \
  cfg:default.paths.plugins=/tmp/sc-grafana/plugins \
  cfg:default.server.http_addr=127.0.0.1 cfg:default.server.http_port=19300
```

Homebrew 版 Grafana 13 **不内置** prometheus 数据源插件，首次启动会自动联网安装，
日志里能看到 `msg="Plugin successfully installed" pluginId=prometheus`；离线机器请预先
准备插件目录，或直接用官方镜像（镜像内置）。若 `options.path` 的环境变量插值不生效
（老版本），生成一份改写过的 provisioning 副本再启动：

```bash
mkdir -p /tmp/sc-grafana/provisioning
cp -R deploy/grafana/provisioning/. /tmp/sc-grafana/provisioning/
sed -i '' "s|\${SC_DASHBOARDS_PATH}|$PWD/deploy/grafana/dashboards|" \
  /tmp/sc-grafana/provisioning/dashboards/dashboard.yml
```

**容器（未在本机执行）**：

```bash
docker run -d --name grafana -p 19300:3000 \
  -v "$PWD/deploy/grafana/provisioning:/etc/grafana/provisioning:ro" \
  -v "$PWD/deploy/grafana/dashboards:/var/lib/grafana/dashboards:ro" \
  -e SC_DASHBOARDS_PATH=/var/lib/grafana/dashboards grafana/grafana:13.2.3
```

## F. 验证数据源与仪表盘真的生效

```bash
G=http://127.0.0.1:19300; A=admin:admin
curl -s -u $A $G/api/datasources | python3 -m json.tool           # uid=prometheus, readOnly=true
curl -s -u $A "$G/api/search?type=dash-db" | python3 -m json.tool  # 能看到 sc-micrometer-overview
# 端到端：让 Grafana 代理去问 Prometheus（这一步通了，面板才有数据）
curl -s -u $A --get --data-urlencode 'query=up{application="springboot-basics"}' \
  "$G/api/datasources/proxy/uid/prometheus/api/v1/query"
```

浏览器打开 `http://127.0.0.1:19300`（默认 admin/admin）→ Dashboards →
「Spring Cloud 教学 · 指标监控总览（Micrometer/Prometheus）」。

## G. 导入仪表盘的三种方式

1. **provisioning（推荐，本目录已配好）**：JSON 放进 `deploy/grafana/dashboards/`，
   重启 Grafana 或等 `updateIntervalSeconds: 30` 自动扫描；`disableDeletion: true`
   表示 UI 上删不掉（以磁盘文件为准）。
2. **UI 导入**：Dashboards → New → Import → 粘贴 JSON → 选 Prometheus 数据源 → Import。
   JSON 里的数据源 uid 是 `prometheus`，若你的 uid 不同，导入时用下拉框重选一次。
3. **HTTP API**：
   ```bash
   curl -s -u admin:admin -X POST http://127.0.0.1:19300/api/dashboards/db \
     -H 'Content-Type: application/json' \
     -d "{\"dashboard\":$(cat deploy/grafana/dashboards/spring-cloud-micrometer-overview.json),\"overwrite\":true}"
   ```

## H. 常见坑（本机实测踩过）

1. **端口冲突**：共享 Prometheus 在 9090、Grafana 在 3000；自建实例用 19090/19300。
   报 `bind: address already in use` 时先 `lsof -nP -iTCP:19090 -sTCP:LISTEN`。
2. **加了依赖没重启**：pom/yml 改动对已运行的 JVM 无效。
3. **`up=1` 但没样本**：`scrape_samples_scraped` 为 0，通常是被 `metric_relabel_configs` 全 drop 了。
4. **目标标签与指标标签同名**：本配置的 target 标签与 Micrometer 的
   `management.metrics.tags.application` 同名，默认（`honor_labels: false`）下冲突的目标标签
   会被改名成 `exported_application`（实测在 `api/v1/query` 返回里两者同时出现）；
   `prometheus.yml` 用 `labeldrop` 丢掉了这个冗余标签。
5. **把没暴露端点的服务写进 targets**：会一直 `up=0` + `lastError=404`，不是配置错。
6. **Grafana 报 `Unable to find datasource plugin`**：见 E 节，数据源插件没装好。
7. **面板全空、报 `Datasource prometheus was not found`**：JSON 里 `datasource.uid` 与
   provisioning 的 `uid` 不一致。
8. **P95 显示 nan**：该 URI 在时间窗内样本太少，或没开直方图
   （`management.metrics.distribution.percentiles-histogram.http.server.requests=true`）；
   只有 `_count/_sum/_max` 是算不出分位数的。
9. **提示 `.../provisioning/plugins: no such file or directory`**：缺该子目录只是警告。
10. **缓存/连接池指标时有时无**：Caffeine 与 HikariCP 的指标「用到才绑定」，
    先打几次业务接口再看。

## I. 本节实测记录

| 组件 | 端口 | PID | 启动要点 |
| --- | --- | --- | --- |
| springboot-basics | 18010 | 82189 | `java -jar springboot-basics/target/*.jar --server.port=18010` |
| Prometheus（自建） | 19090 | 87889 | `--config.file=deploy/prometheus.yml --storage.tsdb.path=/tmp/sc-obs/prom-data` |
| Grafana（自建） | 19300 | 90165 | `cfg:default.paths.provisioning=<副本>` + `SC_DASHBOARDS_PATH=<repo>/deploy/grafana/dashboards` |

`promtool check config` 通过；`python3 -m json.tool` 校验仪表盘 JSON 合法；
仪表盘 24 条面板查询经 Grafana `/api/ds/query` 全部 OK（0 失败）。

**收尾**：`kill "$(lsof -nP -iTCP:19090 -sTCP:LISTEN -t)"`、
`kill "$(lsof -nP -iTCP:19300 -sTCP:LISTEN -t)"`。
不要 `pkill prometheus` / `pkill grafana`，会连带杀掉共享基础设施里的实例。
