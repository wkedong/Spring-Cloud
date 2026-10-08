# Spring-Cloud

一套**可完整运行、可逐项验证**的 Spring Cloud 教学项目：14 个模块、31 篇文档，
从注册中心/配置中心/声明式调用/容错/网关/追踪这条核心链路，一路讲到
Spring Boot 本体深化、新一代组件栈（Nacos / Sentinel / Stream / Security / Seata）
与工程化运维（可观测、容器化、CI/CD）。

教学原则只有三条：**代码可跑、配置自包含、结论有实测输出**。
每篇文档的「动手验证」小节里的命令与输出，都是在本机真实执行后抄回来的。

技术栈：Spring Boot 2.7.18 · Spring Cloud 2021.0.9 · Spring Cloud Alibaba 2021.0.5.0 · Java 8（用 JDK 17 编译）。

## 学习主线（5 个阶段，31 篇）

### 阶段一 · 核心链路：能跑起来并看懂每个角色

| 文档 | 主题 | 模块 |
| --- | --- | --- |
| [00 · 项目总览](docs/00-项目总览.md) | 整体架构、模块职责、服务拓扑 | — |
| [01 · 服务注册与发现](docs/01-服务注册与发现.md) | Eureka：谁在线、在哪里 | `eureka` |
| [02 · 配置中心](docs/02-配置中心.md) | Config Server（JDBC 后端）+ Flyway | `config` |
| [03 · 服务消费](docs/03-服务消费.md) | `@LoadBalanced` RestTemplate + 负载均衡 | `service-consumer(-ribbon)` |
| [04 · 声明式调用 Feign](docs/04-声明式调用Feign.md) | OpenFeign、multipart 文件透传 | `service-consumer-feign` |
| [05 · 服务容错保护](docs/05-服务容错保护.md) | Resilience4j 熔断 + 降级 fallback | `service-consumer-ribbon-hystrix` |
| [06 · 服务网关](docs/06-服务网关.md) | Spring Cloud Gateway 路由 | `zuul` |
| [07 · 服务追踪](docs/07-服务追踪.md) | Sleuth 3.1 + 官方 Zipkin | 各业务模块 |
| [08 · 升级迁移指南](docs/08-升级迁移指南.md) | Edgware → 2021.0 新旧全量对照 | 全部 |
| [09 · 本地运行指南](docs/09-本地运行指南.md) | 全模块启动矩阵、验证清单、踩坑表 | 全部 |

### 阶段二 · Spring Boot 本体深化：先把单体写扎实

| 文档 | 主题 | 模块 |
| --- | --- | --- |
| [10 · Web 层工程化](docs/10-Web层工程化.md) | 统一响应体与异常、参数校验、拦截器/过滤器、traceId 串联 | `springboot-basics` |
| [11 · 配置体系](docs/11-配置体系.md) | profile、配置优先级、`@ConfigurationProperties`、多环境切换 | `springboot-basics` |
| [12 · 数据访问与事务](docs/12-数据访问与事务.md) | JdbcTemplate/MyBatis、事务传播、自调用失效 | `springboot-basics` |
| [13 · 缓存、异步与定时任务](docs/13-缓存异步与定时任务.md) | `@Cacheable`/`@Async`/`@Scheduled` 与线程池、自调用坑 | `springboot-basics` |
| [14 · 可观测与运维](docs/14-可观测与运维.md) | Actuator 端点、健康检查、指标、优雅停机 | `springboot-basics` |
| [15 · 自动配置与自定义 Starter](docs/15-自动配置与自定义Starter.md) | 条件装配、`AutoConfiguration.imports`、自定义 starter | `basics-audit-spring-boot-starter` |
| [16 · 测试策略](docs/16-测试策略.md) | 切片测试、MockMvc、`@MockBean`、测试配置隔离 | `springboot-basics` |

### 阶段三 · Spring Cloud 组件进阶：把核心链路用到生产级

| 文档 | 主题 | 模块 |
| --- | --- | --- |
| [17 · Feign 进阶](docs/17-Feign进阶.md) | 请求头拦截、日志级别、ErrorDecoder、超时与重试、降级工厂 | `service-consumer-feign` |
| [18 · 负载均衡与灰度发布](docs/18-负载均衡与灰度发布.md) | 自定义 LoadBalancer、灰度路由、实例缓存、LB 层重试 | `service-consumer-ribbon` |
| [19 · Gateway 进阶](docs/19-Gateway进阶.md) | GlobalFilter、统一鉴权、限流、CORS、Retry 过滤器 | `zuul` |
| [20 · Resilience4j 全家桶](docs/20-Resilience4j全家桶.md) | Retry/Bulkhead/RateLimiter/TimeLimiter/CircuitBreaker 与 Decorators | `service-consumer-ribbon-hystrix` |
| [21 · 配置刷新与链路追踪进阶](docs/21-配置刷新与链路追踪进阶.md) | `@RefreshScope`、手动/自动刷新、自定义 span、baggage 透传 | `service-producer` |

### 阶段四 · 新一代组件栈：另一条技术路线

| 文档 | 主题 | 模块 |
| --- | --- | --- |
| [22 · Spring Security 与 JWT](docs/22-SpringSecurity与JWT.md) | 组件式过滤链、JWT 签发校验、URL/方法级鉴权、网关鉴权 | `security-demo` |
| [23 · Sentinel 流控与降级](docs/23-Sentinel流控与降级.md) | 流控/熔断/热点/系统规则、Dashboard、与 Resilience4j 对照 | `sentinel-demo` |
| [24 · Nacos 注册与配置](docs/24-Nacos注册与配置.md) | 注册 + 配置一体、namespace/group/dataId、动态刷新、优先级 | `nacos-demo` |
| [25 · 消息驱动 Spring Cloud Stream](docs/25-消息驱动SpringCloudStream.md) | 函数式 Binding、Kafka Binder、消费组、重试与 DLQ | `stream-demo` |
| [26 · 分布式事务 Seata](docs/26-分布式事务Seata.md) | AT 模式、全局锁、undo_log、`@GlobalTransactional` 回滚 | `seata-demo` |

### 阶段五 · 工程化与运维：把系统交付出去

| 文档 | 主题 | 交付物 |
| --- | --- | --- |
| [27 · 指标监控与 Grafana](docs/27-指标监控与Grafana.md) | Micrometer + Prometheus 抓取 + Grafana 看板 | `deploy/prometheus.yml`、`deploy/grafana/` |
| [28 · 日志聚合](docs/28-日志聚合.md) | 结构化日志、traceId 检索、日志文件与轮转 | 各模块 logback 配置 |
| [29 · 容器化部署](docs/29-容器化部署.md) | 分层镜像（`jarmode=layertools`）、compose 编排、健康检查 | `deploy/Dockerfile`、`deploy/docker-compose.yml` |
| [30 · CI/CD 与工程化收尾](docs/30-CICD与工程化收尾.md) | 流水线分层、制品、依赖治理、收尾清单 | `.github/workflows/ci.yml` |

每篇结构统一：**学什么 → 核心代码 → 关键机制与易错点 → 动手验证 → 思考点**。
建议顺序：先读 00，再照 09 把核心链路跑起来；阶段二~五可独立选读，每个模块都能单独启动（见 09 的运行矩阵）。

## 模块与端口矩阵

| 模块（目录） | 端口 | 教学主题 | 关键组件 | 外部依赖 |
| --- | --- | --- | --- | --- |
| `eureka` | 6060 | 服务注册与发现 | Eureka Server | — |
| `config` | 6010 | 配置中心（JDBC 后端） | Config Server + Flyway 8.5 | MySQL |
| `zuul` | 6050 | 服务网关（目录名为历史名） | **Spring Cloud Gateway** | — |
| `service-producer` | 6070 / 6080 / 6090 | 服务提供者、灰度元数据 | MyBatis 2.3 + MySQL 8 | MySQL、Config |
| `service-consumer` | 7010 | 服务消费 | RestTemplate + LoadBalancer | Config |
| `service-consumer-feign` | 7020 | 声明式调用、拦截器/解码器/降级 | **OpenFeign** + Resilience4j | Config |
| `service-consumer-ribbon` | 7030 | 灰度负载均衡与重试 | **LoadBalancer** | Config |
| `service-consumer-ribbon-hystrix` | 7040 | 容错全家桶 | **Resilience4j** | Config |
| `springboot-basics` | 8010 | Boot 本体（Web/配置/数据/缓存/运维/测试） | Boot + H2 | — |
| `basics-audit-spring-boot-starter` | — | 自动配置与自定义 Starter | Boot 自动配置 | — |
| `nacos-demo` | 8210 / 8211 | 注册 + 配置一体 | **Spring Cloud Alibaba Nacos** | Nacos |
| `sentinel-demo` | 8220 | 流控与熔断降级 | **Sentinel** | 可选 Dashboard |
| `stream-demo` | 8230 | 消息驱动 | **Spring Cloud Stream** + Kafka Binder | Kafka |
| `security-demo` | 8240 | 认证授权 | **Spring Security 5.7** + Nimbus JWT | — |
| `seata-demo/seata-order` | 8250 | 分布式事务发起方 | **Seata AT** | Seata Server、MySQL |
| `seata-demo/seata-inventory` | 8260 | 分布式事务参与方 | **Seata AT** | Seata Server、MySQL |
| （基础设施） | 3306 / 8848 / 9092 / 8091 / 9090 / 3000 / 9411 | MySQL / Nacos / Kafka / Seata / Prometheus / Grafana / Zipkin | 单机版即可 | — |

> 目录名 `zuul` / `ribbon` / `hystrix` 是沿用早期工程结构的历史名，
> 内部实现已全部替换为现役组件（Gateway / LoadBalancer / Resilience4j），
> 保留旧名是为了让「升级迁移」这件事在目录层面也看得见，对照见 [docs/08](docs/08-升级迁移指南.md)。

## 服务拓扑

```mermaid
flowchart LR
    C[curl / 客户端] --> GW["gateway :6050<br/>鉴权·限流·重试"]
    GW -->|"lb://service-producer"| P["service-producer<br/>:6070 v1 / :6080 v2"]
    GW -.->|"lb://service-consumer*"| CO["consumer :7010 / feign :7020<br/>ribbon :7030 / hystrix :7040"]
    CO -->|"负载均衡 + 灰度"| P
    P & CO -.注册.-> EU["eureka :6060"]
    P & CO -.拉配置.-> CF["config :6010"]
    CF --- DB[(MySQL :3306)]
    P & CO -.span 上报.-> ZK["zipkin :9411"]

    subgraph 阶段四：新一代组件栈
        NC["nacos-demo :8210"] --- NACOS[(Nacos :8848)]
        ST["sentinel-demo :8220"]
        SM["stream-demo :8230"] --- KAFKA[(Kafka :9092)]
        SEC["security-demo :8240"]
        SEA["seata-order :8250"] -.->|"AT 全局事务"| SEAI["seata-inventory :8260"]
        SEA & SEAI --- TC[(Seata Server :8091)]
    end
    subgraph 阶段二：Boot 本体
        SB["springboot-basics :8010<br/>+ 自定义 starter"]
    end
    subgraph 阶段五：运维
        PROM[Prometheus :9090] --> GRAF[Grafana :3000]
    end
    P & CO & SB -. /actuator/prometheus .-> PROM
```

## 快速开始

```bash
# 0) 工具链：Java 8 源码，用 JDK 17 编译（Maven 3.6+）
export JAVA_HOME=$(/usr/libexec/java_home -v 17)   # macOS；Linux 用对应 JDK 17 路径

# 1) 基础设施：MySQL(3306) + Zipkin(9411)
docker compose up -d

# 2) 构建
mvn clean package -DskipTests

# 3) 按依赖顺序启动核心链路（完整步骤见 docs/09）
java -jar eureka/target/eureka-0.0.1-SNAPSHOT.jar
java -jar config/target/config-0.0.1-SNAPSHOT.jar            # 首次 Flyway 自动建表灌数据
java -jar service-producer/target/service-producer-0.0.1-SNAPSHOT.jar --spring.profiles.active=peer1   # 6070 v1
java -jar service-producer/target/service-producer-0.0.1-SNAPSHOT.jar --spring.profiles.active=peer2   # 6080 v2
java -jar service-consumer/target/service-consumer-0.0.1-SNAPSHOT.jar
java -jar service-consumer-feign/target/service-consumer-feign-0.0.1-SNAPSHOT.jar
java -jar service-consumer-ribbon/target/service-consumer-ribbon-0.0.1-SNAPSHOT.jar
java -jar service-consumer-ribbon-hystrix/target/service-consumer-ribbon-hystrix-0.0.1-SNAPSHOT.jar
java -jar zuul/target/zuul-0.0.1-SNAPSHOT.jar                # 6050

# 4) 验证（等注册表收敛 30~60 秒）
curl http://localhost:6050/service-consumer/testGet
# → Hello, Spring Cloud! My port is 6070 Get info is testGet Success

# 5) 只跑 Boot 本体（不需要任何中间件）
java -jar springboot-basics/target/springboot-basics-0.0.1-SNAPSHOT.jar
curl http://localhost:8010/actuator/health
```

> 提示：服务刚启动时注册表需要 30~60 秒收敛（Eureka 服务端只读缓存 + 客户端拉取间隔），
> 期间调用可能返回 `No servers available` / 网关 503，属正常现象。

## 分支说明

`develop` 为教学主线（即本 README 描述的全部内容）；`master` 已与 develop 对齐，作为对外稳定镜像。

> **旧版实现已从分支退场**：退场前的 master 提交为 `f52281f`（Spring Boot 1.5.2 ·
> Cloud Edgware.SR5，含自建 zipkin 模块、Zuul/Hystrix/Ribbon 原始写法）。
> 需要追溯旧写法时：`git show f52281f:<文件路径>`，或
> `git worktree add /tmp/legacy f52281f` 检出一份完整旧版对照；改动清单见 [docs/08](docs/08-升级迁移指南.md)。

## 本仓库怎么读

1. **想快速看到效果**：`docker compose up -d` → 按上面第 3 步启动 → 跑第 4 步验证。
2. **想系统学**：按阶段一 → 五顺序读；每篇的「动手验证」都给了可复制的命令与真实输出。
3. **只想学某一块**：阶段二~五的模块互不依赖（端口不冲突），单独 `mvn -pl <模块> -am package` 后启动即可。
4. **想对照旧写法**：用上面 `git worktree` 命令检出旧版，配合 [docs/08](docs/08-升级迁移指南.md) 的新旧对照表看。
