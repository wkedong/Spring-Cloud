# 24 · Nacos：注册中心与配置中心

> 模块：`nacos-demo/`（8210，可再起 8211 演示多实例）。
> 前置：需要本地 **Nacos Server 3.x**（standalone；Nacos 客户端 3.1.1 **必须**配服务端 3.x，见「版本与配置要点」的踩坑），
> 启动方式见 docs/09-本地运行指南.md 与本文「动手验证」。

## 学什么

前 23 篇里「注册」归 Eureka、「配置」归 Config：两套模型、两个客户端、两种排错方式。Nacos 把它们合成一个组件——一个服务端地址、一个客户端 jar，且配置改动**不重启就生效**。三件事值得单独学：

1. **配置热更新**：客户端与服务端长轮询，配置一变即推送并触发 `RefreshEvent`（实测新增 key **12 秒内**生效，无需重启）；对比 docs/02 的 Config：改完要 `/actuator/refresh` 或重启。
2. **三级模型**：`namespace`（环境/租户隔离）→ `group`（同环境内业务分组）→ `dataId`（一个配置文件），替代「多套 Eureka + 多套 Config」的土办法。
3. **dataId 算法**：`${spring.application.name}[-${profile}].${file-extension}`，外加 `shared-configs` 共享配置——**谁能覆盖谁**必须门儿清，否则「改了没生效」会查半天。注意 Spring Cloud Alibaba 2025.1.0.0 起 **dataId 不再自动推导**：每条都要显式写进 `spring.config.import`，而 import 列表的顺序就是优先级。

本模块用四个探针 bean 把第 1、3 条做成可对照实验，下面所有结论均来自本机实测（见「动手验证」）。

## 核心代码

接入方式写在 `application.yml`（`bootstrap.yml` 那条引导路径已废弃，原因见「版本与配置要点」）：远端配置用 Config Data API 显式导入。

```yaml
spring:
  application:
    name: nacos-demo          # 决定默认 dataId 前缀：nacos-demo.properties
  profiles:
    active: dev               # profile 专有 dataId 需要自己再写一条 import（不再自动拼）
  config:
    import:
      # 三条 dataId 显式写全；import 列表顺序即优先级——后写的赢（见「覆盖顺序」实测）
      - optional:nacos:nacos-demo.properties?group=DEFAULT_GROUP&refreshEnabled=true
      - optional:nacos:nacos-demo-dev.properties?group=DEFAULT_GROUP&refreshEnabled=true
      - optional:nacos:nacos-demo-shared.properties?group=DEFAULT_GROUP&refreshEnabled=true
  cloud:
    nacos:
      discovery: { server-addr: 127.0.0.1:8848, metadata: { instance-port: "8210" } }  # 端口按实例覆盖，见启动命令
      config:
        server-addr: 127.0.0.1:8848
        file-extension: properties     # dataId 后缀；显式 import 时后缀已含在 dataId 里，此项仅作兼容保留
        refresh-enabled: true          # 打开长轮询推送 → 配置变更自动触发 RefreshEvent
```

`optional:` 前缀 = 配置中心不可用时也要能启动（教学/本地常用）；去掉它，拉不到配置就启动失败。
原 `shared-configs` 的等价写法就是第三条 import（多应用共用同一 dataId）——注意它**不再是优先级最低**的，
config-import 模式下「最后写的那条 import」优先级最高。

按服务名调用：URL 里只有服务名、没有 IP，`@LoadBalanced` 在发请求前把它换成某个实例的 host:port。

```java
@Bean @LoadBalanced                       // Spring Cloud LoadBalancer 默认实现是 RoundRobinLoadBalancer（轮询）
public RestTemplate loadBalancedRestTemplate() { return new RestTemplate(); }
ResponseEntity<ApiResponse<InstanceView>> resp = restTemplate.exchange(   // service/DiscoveryService#lbCall()
        "http://nacos-demo/nacos/instance-info", HttpMethod.GET, null,
        new ParameterizedTypeReference<ApiResponse<InstanceView>>() {});
```

动态刷新的正反面——差别只有 `@RefreshScope` 一个注解：

```java
@Component @RefreshScope                   // 刷新事件销毁实例 → 重新解析占位符 → 拿到新值
public class RefreshValueProbe { @Value("${nacos.demo.title:<未配置>}") private String title; }
@Component                                 // 单例，启动时注入一次，之后永不更新
public class FrozenValueProbe  { @Value("${nacos.demo.title:<未配置>}") private String title; }
```

验证入口（统一响应体 `ApiResponse{code,message,data,traceId,timestamp}`）：

| 端点 | 作用 |
| --- | --- |
| `GET /nacos/instance-info` | 实例自述（真实端口/实例 ID/元数据），同时是 lb-call 的被调方 |
| `GET /nacos/lb-call` | 按服务名自调，返回**本次命中的实例端口**与累计命中分布 |
| `GET /nacos/config/preview` | 实际拉到的 dataId + 四个探针取值 + 刷新次数 + uptime |
| `GET /nacos/config/priority` | 属性源顺序 + 每个 key 的生效来源与全部候选值 |
| `GET /nacos/pitfall/checklist` | 踩坑自检：bootstrap / import 检查 / 坐标 / 松散绑定 / Eureka 对照 |

## 版本与配置要点

| 要点 | 结论（本机实测） |
| --- | --- |
| 配置接入方式 | `bootstrap.yml` + `spring-cloud-starter-bootstrap` 已废弃（Spring Cloud 2025.1 起不再加载）；改 `spring.config.import: optional:nacos:<dataId>?group=DEFAULT_GROUP&refreshEnabled=true`，`optional:` = 配置中心不可用也能启动 |
| dataId 推导 | 2021.x 自动拼 `${spring.application.name}[-${profile}].${file-extension}`；2025.1 起**不再推导**，默认 / profile 专有 / shared 三条 dataId 都必须显式写进 import |
| 版本对应 | SCA `2025.1.0.0` ↔ Spring Cloud `2025.1.3` ↔ Boot `4.0.8`，别跨代混搭；**Nacos 客户端 3.1.1 必须配服务端 3.x**（本机服务端 `3.2.4` standalone，内嵌 derby）；客户端仍用 8848 + 9848/9849 gRPC，**控制台在独立端口 8080** |
| Nacos 3.x 的 API 变化 | **v1/v2 配置 API 已移除**：`/nacos/v1/cs/configs` 直接 404；发布配置用 `POST /nacos/v3/admin/cs/config`，读取用 `GET /nacos/v3/client/cs/config`（见「动手验证」） |
| Nacos 3.x 的鉴权默认值 | admin/console 鉴权**默认开启**，且只关 `nacos.core.auth.enabled` 不够（v3 admin API 仍报 access denied / User not found）；本地教学环境要把 `nacos.core.auth.admin.enabled` 与 `nacos.core.auth.console.enabled` 一起关掉。3.x 还要求显式配置 `nacos.core.auth.server.identity.key/value`，否则启动报 `errCode: 50002, Empty identity` |
| namespace / group | namespace 留空 = `public`（客户端 3.x 把空命名空间归一化为 `public`，与老版本「空串 ID」的语义已不同，见下方踩坑）；控制台显示的是「名称」、配置里要填「ID」；group 是同一命名空间内的二级隔离。**本机只用 public + DEFAULT_GROUP，未做跨命名空间隔离实测** |

**覆盖顺序（同一 key 写进三个 dataId，实测）**：`nacos-demo-shared.properties`（最后一条 import）> `nacos-demo-dev.properties` > `nacos-demo.properties`（默认）。证据：`nacos.demo.title` 三处分别写入，生效值最终取 shared（日志 `收到配置刷新事件（第 1 次），最新 title = title-from-shared-configs`）。
> 旧版本行为：bootstrap 引导时代是 `profile > 默认 > shared-configs`（旧实测结论；升级后顺序反转，因为它由 import 书写顺序决定）——**升级后必须按新顺序理解，别再套老口诀**。

**配置优先级（`/nacos/config/priority` 实测）**：**导入的 Nacos 配置优先于导入它的本地 `application.yml`**（`nacos.demo.priority` 的远端值胜出）。要强行覆盖 Nacos 的取值，最稳的仍是改 dataId 内容；命令行参数与远端谁赢，本次未单独复测，请以本机端点现场输出为准。
> 旧版本行为：bootstrap 时代 Nacos 属性源被插到 `commandLineArgs` 之前，连命令行参数都压不过远端；该行为随 bootstrap 引导一起废弃，命令行与远端谁赢请以本机 `/nacos/config/priority` 现场输出为准。

**踩坑：客户端 3.1.1 配 2.x 服务端 = 配置「订阅成功但全是空」**。本机把客户端升到 3.1.1、服务端仍是 2.3.2 时，三条 dataId 全部加载成空，日志是：

```text
[Nacos Config] config[dataId=nacos-demo.properties, group=DEFAULT_GROUP] is empty
```

根因是 `Constants.DEFAULT_NAMESPACE_ID` 从 `""` 变成了 `"public"`——客户端按新默认命名空间 `public` 去服务端查配置，而 2.x 服务端里这些配置的命名空间 ID 是空串，于是永远查不到（订阅是成功的，所以只会看到「空配置」这种误导性日志）。
把服务端升到 **3.2.4** 后恢复正常：`[Nacos Config] Load config[dataId=nacos-demo.properties, group=DEFAULT_GROUP] success`。
结论：**Nacos 客户端 3.x 与服务端 2.x 不支持混用**，两端一起升。

**踩坑：Spring Cloud 2020+ 的 config import 检查**（不写 `spring.config.import` 时启动即失败）：

```text
Description:  No spring.config.import property has been defined
Action:       Add a spring.config.import=nacos: property to your configuration.
              If configuration is not required add spring.config.import=optional:nacos: instead.
              To disable this check, set spring.cloud.nacos.config.import-check.enabled=false.
```

拦截条件三者同时成立：非 bootstrap + 非 legacy + `nacos.config.enabled` 且 `import-check.enabled` 未置 false。三条出路：① 引 bootstrap starter（**已废弃路线**，2025.1 起不再推荐）；② 显式写 `spring.config.import=optional:nacos:nacos-demo.properties`（**本模块做法**；优先级**由 import 列表顺序决定，后写的优先**）；③ 关掉 `import-check` —— 只关检查，**不会**加载配置。

**踩坑：`@Value` 不做松散绑定**。Nacos 里写 `nacos.demo.profile-value` 时，`@Value("${nacos.demo.profileValue}")` 静默落到默认值（实测取到 `null`）；只有 `@ConfigurationProperties` 会在 bind 阶段把驼峰归一化成 kebab-case。

**Eureka + Config → Nacos 对照**（`eureka/`、`config/` 是 Edgware 迁移来的对照物）：

| Eureka + Config | Nacos |
| --- | --- |
| `eureka.client.serviceUrl.defaultZone=http://localhost:6060/eureka/` | `spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848`（只有 host:port） |
| `spring.cloud.config.uri` + name/profile/label 三元组 | `spring.cloud.nacos.config.server-addr` + dataId/group/namespace；无命名空间概念 vs `namespace` + `group` 两级隔离 |
| 改配置需重启或 `/actuator/refresh` | `refresh-enabled` + `@RefreshScope`，推送即生效 |
| `spring-cloud-starter-netflix-eureka-client` | `spring-cloud-starter-alibaba-nacos-discovery` + `-config` |

## 动手验证

```bash
# 0. Nacos 服务端 3.x（standalone）启动要点（本机 3.2.4，zip 包取自 download.nacos.io，
#    解压后 export JAVA_HOME=$(/usr/libexec/java_home -v 17) && bash nacos/bin/startup.sh -m standalone）：
#    ① nacos.core.auth.server.identity.key/value 必须显式配置，否则启动报 errCode: 50002, Empty identity；
#    ② 发行包的 bin/startup.sh 已经带上了 -Dloader.path=<解压目录>/plugins（Derby 驱动在 plugins 里）；
#       如果你是手工 `java -jar nacos-server.jar` 启动，这个参数要自己补，否则内嵌库起不来；
#    ③ 默认开启 admin/console 鉴权，本地教学环境在 conf/application.properties 里关掉这三项：
#       nacos.core.auth.enabled=false、nacos.core.auth.admin.enabled=false、nacos.core.auth.console.enabled=false
#       （后两项分别管 /v3/admin/* 与 /v3/console/*；只关第一项不够——v3 admin API 照样报 access denied / User not found）
#    控制台在独立端口 8080：http://127.0.0.1:8080

# 1. 发布三个 dataId（Nacos 3.x：v1/v2 配置 API 已移除，/nacos/v1/cs/configs 会 404，改用 v3 admin API；
#    教学环境关掉了 admin/console 鉴权，所以下面的命令免登录；发布异步落 derby，返回 true 后等 2~3s 再读；
#    下面是默认 dataId 的示例，dev 写明 title/profile-value，shared 写明 title/order-probe/shared-value）
curl -s -X POST http://127.0.0.1:8848/nacos/v3/admin/cs/config \
  --data-urlencode 'dataId=nacos-demo.properties' --data-urlencode 'groupName=DEFAULT_GROUP' --data-urlencode 'namespaceId=public' \
  --data-urlencode 'type=properties' \
  --data-urlencode $'content=nacos.demo.title=title-from-default-dataId\nnacos.demo.priority=from-nacos-remote\nnacos.demo.order-probe=order-probe-in-default-dataId\nnacos.demo.feature-flag=false\nnacos.demo.max-batch-size=10'

# 读回校验（v3 client API；dataId 必须是完整文件名，因为客户端不再自动拼后缀）
curl -s 'http://127.0.0.1:8848/nacos/v3/client/cs/config?dataId=nacos-demo.properties&groupName=DEFAULT_GROUP&namespaceId=public'

# 2. 构建 + 起双实例
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
mvn -B -pl nacos-demo -am package -DskipTests
nohup java -jar nacos-demo/target/nacos-demo-0.0.1-SNAPSHOT.jar --server.port=8210 > /tmp/nacos-demo-8210.log 2>&1 &
nohup java -jar nacos-demo/target/nacos-demo-0.0.1-SNAPSHOT.jar --server.port=8211 \
      --spring.cloud.nacos.discovery.metadata.instance-port=8211 > /tmp/nacos-demo-8211.log 2>&1 &
```

**升级后实测摘要**（Nacos 客户端 3.1.1 + 服务端 3.2.4）：

```text
# 配置加载：三条 dataId 全部成功（服务端还是 2.3.2 时这里是 is empty 的 WARN，见「版本与配置要点」踩坑）
[Nacos Config] Load config[dataId=nacos-demo-shared.properties, group=DEFAULT_GROUP] success
[Nacos Config] Load config[dataId=nacos-demo-dev.properties, group=DEFAULT_GROUP] success
[Nacos Config] Load config[dataId=nacos-demo.properties, group=DEFAULT_GROUP] success
# 覆盖顺序：后写的 import 赢 —— 同一 key 最终取 shared
收到配置刷新事件（第 1 次），最新 title = title-from-shared-configs
# 优先级：导入的 Nacos 配置 > 本地 application.yml
nacos.demo.priority => from-nacos-remote
# 动态刷新：往 dataId 里新增 key，12 秒内 /nacos/config/preview 就能看到新 key，无需重启
```

**升级前实测输出摘要存档**（2026-10-08，bootstrap 引导 + Nacos 2.3.2 + Corretto 17；下面的实例列表 API 与覆盖顺序都是旧版本行为）：

```text
# 注册：Nacos OpenAPI 实例列表（两个实例，元数据各自带端口）
curl 'http://127.0.0.1:8848/nacos/v1/ns/instance/list?serviceName=nacos-demo'
  ip=192.168.85.52 port=8210 healthy=True metadata={"from":"nacos-demo-teaching","instance-port":"8210"}
  ip=192.168.85.52 port=8211 healthy=True metadata={"from":"nacos-demo-teaching","instance-port":"8211"}
# 负载均衡：连打 4 次 /nacos/lb-call，命中端口严格轮询
  callSeq=1..4 命中=8210/8211/8210/8211，累计={"8210":2,"8211":2}
# 配置读取：/nacos/config/preview 实际拉到 4 个属性源（dev=2 键、默认=6 键、shared=3 键，外加一条 SAA 额外
# 探测的「无后缀 dataId」nacos-demo=0 键——服务端 404，日志有 WARN "Ignore the empty nacos configuration…"，不影响覆盖关系）

# 动态刷新：只改 Nacos（不重启、不动本地文件），uptime 68s→75s、refreshEventCount 0→2
  refreshValueProbe（@Value + @RefreshScope）    title-from-profile-v2 → title-from-profile-v3   ✅刷
  frozenValueProbe（@Value 无 @RefreshScope）    保持 title-from-profile-v2                     ❌不刷
  demoProperties 与 plainProperties（@ConfigurationProperties，加不加 @RefreshScope 都一样）
                                                 max-batch-size 20→30、feature-flag true→false   ✅刷
  日志：收到配置刷新事件（第 1 次）… Refresh keys changed: [nacos.demo.feature-flag, nacos.demo.max-batch-size, nacos.demo.title]
  → 必须加 @RefreshScope 的只有「@Value 所在的 bean」；@ConfigurationProperties 由 ConfigurationPropertiesRebinder 重新绑定，
    不加也会刷新——「Nacos 下 @ConfigurationProperties 必须加 @RefreshScope」的说法在本机不成立。

# 优先级（旧版本行为：bootstrap 属性源被插在 commandLineArgs 之前，远端连命令行都压得住）：
#   注意升级后不再如此，谁赢以本机 /nacos/config/priority 现场输出为准（当前实测：远端 > 本地 application.yml）
  nacos.demo.priority   => from-nacos-remote（候选：commandLineArgs=from-command-line、application.yml=from-local-application-yml）
  nacos.demo.local-only => from-command-line（Nacos 里没有这个 key，命令行就赢）
# 清理：只杀自己起的 jar（中间件不动）；临时实例下线后注册表随即为空 → pkill -f "nacos-demo-0.0.1-SNAPSHOT.jar"
```

> 存档里「动态刷新」一段的 `@RefreshScope` 结论（`@Value` 所在的 bean 必须加、`@ConfigurationProperties` 不需要）
> 是 Spring Cloud 的通用刷新语义，升级后仍可据此理解 `@RefreshScope` 的作用范围（本次未逐条复测）。

## 思考点

- **CAP 取舍**：Eureka 是纯 AP（自我保护、可能返回已下线实例）；Nacos 1.x 临时实例走 AP（Distro）、持久实例走 CP（Raft），2.x 换成自研 jRaft。同一注册中心里并存两种一致性语义，对业务意味着什么？
- **配置中心的安全**：本模块配置明文、API 免登录——但这只是因为本地教学环境把 Nacos 3.x **默认开启**的 admin/console 鉴权关掉了（`nacos.core.auth.admin.enabled` / `console.enabled=false`）。生产别照抄：默认就是开的，还要配好 `nacos.core.auth.server.identity.key/value`、按 namespace 授权、口令加密，并且能审计「谁在什么时候改了哪一行」——这些该由谁保证：配置中心、流水线，还是业务自己？
- **灰度发布**：配置灰度（按 IP/标签只推给部分实例）比代码灰度更危险——同一份代码跑了两种逻辑。如果要做，先回答：回滚的判据是什么？
- **命名空间 vs 多集群**：dev/test/prod 用 namespace 省事，但也意味着「一条命令能改到生产」。隔离级别与操作风险如何平衡？
- **本地配置该留什么**：`server.port`、日志级别这类「部署期」参数留本地，「运行期会变」的开关与阈值放 Nacos；什么都往配置中心塞，配置变更本身就成了发布事故源。
