# 24 · Nacos：注册中心与配置中心

> 模块：`nacos-demo/`（8210，可再起 8211 演示多实例）。
> 前置：需要本地 Nacos Server（standalone），启动方式见 docs/09-本地运行指南.md 与本文「动手验证」。

## 学什么

前 23 篇里「注册」归 Eureka、「配置」归 Config：两套模型、两个客户端、两种排错方式。Nacos 把它们合成一个组件——一个服务端地址、一个客户端 jar，且配置改动**不重启就生效**。三件事值得单独学：

1. **配置热更新**：客户端与服务端长轮询，配置一变即推送并触发 `RefreshEvent`；对比 docs/02 的 Config：改完要 `/actuator/refresh` 或重启。
2. **三级模型**：`namespace`（环境/租户隔离）→ `group`（同环境内业务分组）→ `dataId`（一个配置文件），替代「多套 Eureka + 多套 Config」的土办法。
3. **dataId 算法**：`${spring.application.name}[-${profile}].${file-extension}`，外加 `shared-configs` 共享配置——**谁能覆盖谁**必须门儿清，否则「改了没生效」会查半天。

本模块用四个探针 bean 把第 1、3 条做成可对照实验，下面所有结论均来自本机实测（见「动手验证」）。

## 核心代码

配置写在 `bootstrap.yml`（不是 `application.yml`）：dataId 要在「决定配置从哪来」的阶段就定下来。

```yaml
spring:
  application:
    name: nacos-demo          # 决定默认 dataId 前缀
  profiles:
    active: dev               # 必须在这里可见，才会拉 nacos-demo-dev.properties
  cloud:
    nacos:
      discovery: { server-addr: 127.0.0.1:8848, metadata: { instance-port: "8210" } }  # 端口按实例覆盖，见启动命令
      config:
        server-addr: 127.0.0.1:8848
        file-extension: properties
        shared-configs:                        # 共享配置，优先级最低
          - data-id: nacos-demo-shared.properties
            refresh: true
```

按服务名调用：URL 里只有服务名、没有 IP，`@LoadBalanced` 在发请求前把它换成某个实例的 host:port。

```java
@Bean @LoadBalanced                       // 2021.0.x 默认实现是 RoundRobinLoadBalancer（轮询）
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
| Boot 2.4+ 不再默认加载 bootstrap.yml | 必须引 `spring-cloud-starter-bootstrap`；该 starter 里**只有一个 `Marker` 类**，`PropertyUtils.bootstrapEnabled()` 就靠它判定 |
| `--spring.cloud.bootstrap.enabled=false` 能关掉 bootstrap 吗 | **不能**：`bootstrapEnabled = 属性 \|\| MARKER_CLASS_EXISTS`，starter 在就恒为 true；要真关掉只能移除该 starter |
| 版本对应 | SAA `2021.0.5.0` ↔ Spring Cloud `2021.0.x`（本仓库 2021.0.9）↔ Boot 2.7.18，别跨代混搭；Nacos 服务端 2.3.2 standalone（内嵌 derby），客户端用 8848 + 9848/9849 gRPC |
| namespace / group | namespace 留空 = `public`（其 ID 就是空串），控制台显示的是「名称」、配置里要填「ID」；group 是同一命名空间内的二级隔离。**本机只用 public + DEFAULT_GROUP，未做跨命名空间隔离实测** |

**覆盖顺序（同一 key 写进三个 dataId，实测）**：`nacos-demo-dev.properties`（带 profile）> `nacos-demo.properties`（默认）> `nacos-demo-shared.properties`（shared-configs）。证据：`nacos.demo.title` 三处分别写入，生效值取 profile；`nacos.demo.order-probe` 只写在默认与 shared，生效值取默认。

**配置优先级（`/nacos/config/priority` 实测）**：Nacos 远端 > 命令行参数 > 本地 `application.yml`。即使 `--nacos.demo.priority=from-command-line`，生效值仍是 Nacos 的 `from-nacos-remote`（bootstrap 属性源被插到 `commandLineArgs` **前面**）；而 Nacos 里没有的 key，命令行照样覆盖本地 yml。要强行覆盖 Nacos，只能改 dataId 内容。

**踩坑：Spring Cloud 2020+ 的 config import 检查**（不引 starter 且不写 `spring.config.import` 时启动即失败）：

```text
Description:  No spring.config.import property has been defined
Action:       Add a spring.config.import=nacos: property to your configuration.
              If configuration is not required add spring.config.import=optional:nacos: instead.
              To disable this check, set spring.cloud.nacos.config.import-check.enabled=false.
```

拦截条件三者同时成立：非 bootstrap + 非 legacy + `nacos.config.enabled` 且 `import-check.enabled` 未置 false。三条出路：① 引 bootstrap starter（本模块做法）；② `--spring.config.import=optional:nacos:nacos-demo.properties`（实测可启动，优先级改为**由 import 列表顺序决定，后写的优先**）；③ 关掉 `import-check` —— 只关检查，**不会**加载配置。

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
# 1. 发布三个 dataId（API 免登录；发布异步落 derby，返回 true 后等 2~3s 再 GET；下面是默认 dataId 的示例，
#    dev 写明 title/profile-value，shared 写明 title/order-probe/shared-value）
curl -s -X POST http://127.0.0.1:8848/nacos/v1/cs/configs \
  --data-urlencode 'dataId=nacos-demo.properties' --data-urlencode 'group=DEFAULT_GROUP' --data-urlencode 'type=properties' \
  --data-urlencode $'content=nacos.demo.title=title-from-default-dataId\nnacos.demo.priority=from-nacos-remote\nnacos.demo.order-probe=order-probe-in-default-dataId\nnacos.demo.feature-flag=false\nnacos.demo.max-batch-size=10'

# 2. 构建 + 起双实例
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
mvn -B -pl nacos-demo -am package -DskipTests
nohup java -jar nacos-demo/target/nacos-demo-0.0.1-SNAPSHOT.jar --server.port=8210 > /tmp/nacos-demo-8210.log 2>&1 &
nohup java -jar nacos-demo/target/nacos-demo-0.0.1-SNAPSHOT.jar --server.port=8211 \
      --spring.cloud.nacos.discovery.metadata.instance-port=8211 > /tmp/nacos-demo-8211.log 2>&1 &
```

**本机实测输出摘要**（2026-10-08，Nacos 2.3.2 + Corretto 17）：

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

# 优先级：远端 > 命令行 > 本地 yml
  nacos.demo.priority   => from-nacos-remote（候选：commandLineArgs=from-command-line、application.yml=from-local-application-yml）
  nacos.demo.local-only => from-command-line（Nacos 里没有这个 key，命令行就赢）
# 清理：只杀自己起的 jar（中间件不动）；临时实例下线后注册表随即为空 → pkill -f "nacos-demo-0.0.1-SNAPSHOT.jar"
```

## 思考点

- **CAP 取舍**：Eureka 是纯 AP（自我保护、可能返回已下线实例）；Nacos 1.x 临时实例走 AP（Distro）、持久实例走 CP（Raft），2.x 换成自研 jRaft。同一注册中心里并存两种一致性语义，对业务意味着什么？
- **配置中心的安全**：本模块配置明文、API 免登录（`/nacos/v1/auth/login` 返回 `AUTH_DISABLED`）。生产要开鉴权、按 namespace 授权、口令加密，还要能审计「谁在什么时候改了哪一行」——这些该由谁保证：配置中心、流水线，还是业务自己？
- **灰度发布**：配置灰度（按 IP/标签只推给部分实例）比代码灰度更危险——同一份代码跑了两种逻辑。如果要做，先回答：回滚的判据是什么？
- **命名空间 vs 多集群**：dev/test/prod 用 namespace 省事，但也意味着「一条命令能改到生产」。隔离级别与操作风险如何平衡？
- **本地配置该留什么**：`server.port`、日志级别这类「部署期」参数留本地，「运行期会变」的开关与阈值放 Nacos；什么都往配置中心塞，配置变更本身就成了发布事故源。
