# 23 · Sentinel：流控与熔断降级

> 模块：`sentinel-demo/`（8220）。无需注册中心，单机可跑。
> 与 [05-服务容错保护](05-服务容错保护.md) 是同一问题的两条路线：05 讲 Resilience4j（Spring 官方抽象），本篇讲 Sentinel（阿里系），末尾有对照表。

## 学什么

05 篇的 Resilience4j 把熔断做成了漂亮的基础设施，但规则写在配置文件里，**改阈值要重启**。
Sentinel 解决的是另一半问题——**流量是活的，规则也得是活的**：QPS 阈值、热点商品、这台机器扛不住了要自动收口，
都需要「运行时下发 + 控制台可视化」。

本模块用**编程式 API 在启动时加载**四类规则（不依赖控制台，冷启动即可 curl 复现）：

| 规则 | 管什么 | 本模块资源 |
| --- | --- | --- |
| 流控 FlowRule | 单个资源的 QPS / 并发线程数 | `flow-qps`、`flow-concurrent`、`flow-queue` |
| 熔断降级 DegradeRule | 下游变慢或报错时快速失败 | `degrade-slow`、`degrade-exception-ratio`、`degrade-exception-count` |
| 热点参数 ParamFlowRule | 单个**参数值**（爆款商品） | `hotspot` |
| 系统保护 SystemRule | 整台机器的入口总 QPS/RT/线程数 | 全应用入口（默认关闭） |

## 核心代码

**注解决定「保护哪个方法」，规则决定「什么时候拦」**，两者分开：

```java
@GetMapping("/qps")
@SentinelResource(value = "flow-qps", blockHandler = "qpsBlockHandler", fallback = "businessExceptionFallback")
public ApiResponse<Map<String, Object>> qps(@RequestParam(required = false) String note) {
    return flowDemoService.qps(note);
}

public ApiResponse<Map<String, Object>> qpsBlockHandler(String note, BlockException ex) { ... }      // 规则拦的
public ApiResponse<Map<String, Object>> businessExceptionFallback(String note, Throwable t) { ... }  // 代码抛的
```

编程式规则（`config/SentinelRuleConfig`，`@PostConstruct` 加载；控制台点「新增规则」底层调的就是这些方法）：

```java
// 流控：QPS=2 快速失败
FlowRule qps = new FlowRule();
qps.setResource("flow-qps");
qps.setGrade(RuleConstant.FLOW_GRADE_QPS);                      // FLOW_GRADE_THREAD = 并发线程数模式
qps.setCount(2);
qps.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);  // RATE_LIMITER = 排队等待
FlowRuleManager.loadRules(Arrays.asList(qps));

// 熔断：慢调用比例（>500ms 算慢，占比 ≥50%、统计窗口内至少 10 次 → 打开 10s）
new DegradeRule("degrade-slow")
        .setGrade(RuleConstant.DEGRADE_GRADE_RT)   // EXCEPTION_RATIO / EXCEPTION_COUNT
        .setCount(500)                              // 慢调用判定阈值(ms)
        .setSlowRatioThreshold(0.5).setMinRequestAmount(10)
        .setStatIntervalMs(1000).setTimeWindow(10);   // 统计窗口 1s；生产常用 10s
DegradeRuleManager.loadRules(rules);

// 热点参数：只对第 0 个参数（type）的「每个值」限 2 QPS
ParamFlowRuleManager.loadRules(Collections.singletonList(
        new ParamFlowRule("hotspot").setParamIdx(0).setCount(2)));

// 系统保护：入口总 QPS=5（其余维度 -1 = 不启用）
SystemRuleManager.loadRules(Collections.singletonList(rule));
```

Feign 与 Sentinel 整合只要一行配置（`feign.sentinel.enabled: true`），每个远程调用就自动成为受保护的资源：

```java
@FeignClient(name = "self-downstream", url = "http://127.0.0.1:8220",
        fallbackFactory = FeignDownstreamFallbackFactory.class)   // url 直连，不需要注册中心
public interface FeignDownstream { ... }
```

观测端点：`GET /sentinel/rules`（内存里生效的全部规则）、`GET /sentinel/status`（各资源实时统计 + 被拦次数）。

## 关键机制

### blockHandler vs fallback（最常被搞混的一点）

| | `blockHandler` | `fallback` |
| --- | --- | --- |
| 触发原因 | 被**规则**拦住：流控 / 熔断 / 系统保护 / 热点参数 | 业务方法**自己抛异常** |
| 异常来源 | Sentinel 抛 `BlockException`（`FlowException`/`DegradeException`/`ParamFlowException`） | 你的代码（含 Feign 的 `FeignException`、超时） |
| 方法签名 | 参数与原方法一致 + 末尾 `BlockException` | 参数与原方法一致 + 末尾 `Throwable` |
| 是否进入业务方法 | **没有**（方法体根本没执行） | 已进入，中途抛出 |

两者都配时按「谁的原因谁处理」路由，是互补而非二选一。实测（同一资源 `flow-qps`）：
连打 6 次 → 前 2 次通过，第 3 次起 `code=7001 / blockException=FlowException`（fallback 计数 0）；
`/demo/fallback-demo` 第一次必抛 `IllegalStateException` → `code=7002`（blockHandler 计数 0）。

**容易踩的坑**：`BlockException` 是**受检异常**（`extends Exception`），方法没配 `blockHandler` 时它会逃出 Controller，
被 Spring MVC 包成 500。所以「加了 `@SentinelResource` 就万事大吉」是错的——被限流的接口会以 500 暴露给调用方。
本模块的 `GlobalExceptionHandler` 专门给 `BlockException` 加了 429 分支兜住这种情况。

### 四类规则的关键参数

| 规则 | 关键参数与选择依据 |
| --- | --- |
| 流控 | `grade=QPS` 按每秒请求数；`grade=并发线程数` 按「同一瞬间在方法内的线程数」，后者才能保护「慢且吃资源」的下游。`controlBehavior=快速失败` 直接拒绝；`=排队等待`（`RateLimiterController` + `maxQueueingTimeMs`）匀速放行，用响应时间换「不丢请求」 |
| 熔断降级 | `慢调用比例` 是下游只变慢不报错时唯一能提前止损的规则；`异常比例` 适合大流量；`异常数` 适合低流量接口（否则「1 个请求失败 = 100% 异常率」会误判）。`minRequestAmount` 是「样本太少就不判定」的保护 |
| 热点参数 | 只能配在 `@SentinelResource` 资源上（URL 资源拿不到参数值）；配额按**参数值**独立计算，爆款被限流时长尾商品不受影响 |
| 系统保护 | 判定整机入口指标（`Constants.ENTRY_NODE`），只有 `EntryType.IN` 的流量才计入，因此它是所有业务规则之后的最后一道防线 |

**三个最容易踩的坑（都实测过）**：

- **`minRequestAmount` 与统计窗口是两道独立门槛，必须同时满足**。实测：窗口 1s 内并发 **9** 次慢调用**不**熔断，
  **10** 次**必然**熔断——边界精确落在 10。
- **熔断判定发生在「统计窗口结算」时，而不是「第 N 个请求」**。窗口设 10s 时，前 10 次慢调用会全部正常通过，
  要等窗口走完才看到熔断，很容易误判成「规则没生效」。本模块为了 `curl` 好复现，把 `degrade-slow` 的
  `statIntervalMs` 设为 **1s**（语义不变，只是判定更频繁）；代价是顺序请求（每次约 0.8s）永远填不满
  1s 窗口的 10 个样本，所以演示要用**并发**打满窗口。生产取多大，是「多快对下跌做出反应」与
  「样本量是否足够」的权衡。
- **URL 资源名在不同版本里不一样，写错就「规则加载成功但永不命中」**。本模块实测：
  Spring Cloud Alibaba 2021.x 注册的是 `SentinelWebInterceptor`（urlPatterns = `/**`），
  它建的资源名是**纯路径**（`/system/probe`）；而老教程里常见的 `GET:/path` 命名来自
  早期的 `CommonFilter`。**不确定时看 `/sentinel/status` 打出的资源清单**，那是真实值。
  另外 `FlowRuleManager.loadRules()` 是**整体替换**而不是追加——直接 load 一条新规则会把启动时加载的
  三条流控规则全部冲掉（`/system/url-rule` 里做了合并，可参考它的写法）。

### 规则持久化到 Nacos

编程式规则与控制台推送的规则都**只存在内存**，重启即丢。生产做法是接可写数据源：
加 `sentinel-datasource-nacos` 依赖并在 Nacos 建配置（如 `sentinel-demo-flow-rules`，JSON 数组），
然后注册数据源，由 Nacos 监听器驱动（控制台点「新增规则」底层调的也是这批 Manager）：

```java
NacosDataSource<List<FlowRule>> source = new NacosDataSource<>(remoteAddress, groupId, dataId,
        cfg -> JSON.parseObject(cfg, new TypeReference<List<FlowRule>>() {}));
FlowRuleManager.register2Property(source.getProperty());
```

官方 dashboard 默认只把规则推到内存，需改造为「控制台 → Nacos → 各客户端」的单向链路：
客户端只读、控制台只写，避免多实例互相覆盖。

### 与 Resilience4j 对照

| 维度 | Sentinel | Resilience4j（05 篇） |
| --- | --- | --- |
| 规则动态化 / 控制台 | 原生支持运行时改规则，有独立控制台 | 需自己接 Actuator/配置中心，无控制台 |
| 隔离方式 | 并发线程数模式（`grade=并发线程数`），非线程池隔离 | 信号量隔离默认，可配线程池隔离 |
| 规则持久化 | 需接 Nacos/Apollo 数据源 | 天然在 Spring 配置里（配置中心即持久化） |
| 线程模型 | 无额外线程，`并发线程数` 只是计数 | TimeLimiter 需异步执行才生效 |
| Spring 生态契合度 | Alibaba 系依赖，版本线与 Spring Cloud 绑定 | Spring Cloud CircuitBreaker 官方抽象，纯 Spring |
| 额外运维成本 | 多一个 Dashboard 进程（可选） | 无 |

**怎么选**：需要「大促实时调阈值、按参数限流、看板可视化」→ Sentinel；
只需要「下游挂了别拖死我」且不想多一个服务端 → Resilience4j。两者不冲突，可以并存。

## 动手验证

```bash
mvn -B -pl sentinel-demo -am package -DskipTests
nohup java -jar sentinel-demo/target/sentinel-demo-0.0.1-SNAPSHOT.jar > /tmp/sentinel-demo-8220.log 2>&1 &

# 1) 流控转折点：QPS=2，前 2 个通过、第 3 个起被拦（HTTP 200 + 业务码，不是 500）
for i in $(seq 1 6); do curl -s http://127.0.0.1:8220/demo/qps; echo; done
# 第1次 code=0  第2次 code=0  第3次起 code=7001 blockException=FlowException

# 2) 熔断：慢调用比例。用并发把 1s 统计窗口打满（minRequestAmount=10 必须被满足）
#    ① 并发 10 次真实慢调用 → 真实耗时 0.803~0.816s
seq 1 10 | xargs -P 10 -I@ curl -s -o /dev/null "http://127.0.0.1:8220/degrade/slow"
#    ② 紧接着逐个请求 → 快速失败，耗时骤降
for i in 1 2 3 4 5; do curl -s -o /dev/null -w "%{time_total}s\n" http://127.0.0.1:8220/degrade/slow; done
#    0.0028s / 0.0019s / 0.0016s / 0.0019s / 0.0019s   ← 请求没走到业务方法
#    响应体：code=7001 message=熔断降级：DegradeException（快速失败）
#    边界验证：并发 9 次 → 不熔断；并发 10 次 → 熔断（minRequestAmount 被严格遵守）

# 3) 异常数：异常数≥5 打开熔断 15s
for i in $(seq 1 8); do curl -s http://127.0.0.1:8220/degrade/exception-count; echo; done
# 第 1~6 次 code=7002（fallback 兜底，同时被 Sentinel 记成 error）；第 7~8 次 code=7001 DegradeException

# 4) 热点参数：配额按「参数值」独立计算
for i in 1 2 3; do curl -s "http://127.0.0.1:8220/hotspot?type=iphone"; echo; done
curl -s "http://127.0.0.1:8220/hotspot?type=macbook"; echo
# iphone 第1、2次 code=0，第3次 code=7001 ParamFlowException；紧接着 macbook code=0（全新配额）

# 5) blockHandler 与 fallback 各一条命令（同一资源，互不干扰）
curl -s http://127.0.0.1:8220/demo/reset; echo
curl -s http://127.0.0.1:8220/demo/fallback-demo; echo
# code=7002 exception=IllegalStateException ← 业务异常走 fallback，blockHandler 计数=0
for i in 1 2 3; do curl -s http://127.0.0.1:8220/demo/qps; echo; done
# 第3次 code=7001 blockException=FlowException ← 规则拦截走 blockHandler，fallback 计数=0

# 6) 当前生效的全部规则（内存态）
curl -s http://127.0.0.1:8220/sentinel/rules | python3 -m json.tool
# counts: {"flow":3,"degrade":3,"paramFlow":1,"system":0}

# 7) Feign + Sentinel（url 直连本机，不需要注册中心）
curl -s "http://127.0.0.1:8220/feign/call-slow?sleepMs=1200"   # 超过读超时 1000ms，耗时≈1.02s
# code=7003 causeType=feign.RetryableException  causeMessage=Read timed out ...
curl -s "http://127.0.0.1:8220/feign/call-error"                # 下游真实 500
# code=7003 causeType=feign.FeignException$InternalServerError
curl -s "http://127.0.0.1:8220/feign/call-slow?sleepMs=200"     # 正常，验证不是「一律降级」→ code=0

# 8) 入参上限（信任边界）：超限返回 400，而不是拖住线程或伪装成功
curl -s -o /dev/null -w "%{http_code}\n" "http://127.0.0.1:8220/downstream/slow?sleepMs=2000"   # 200
curl -s -o /dev/null -w "%{http_code}\n" "http://127.0.0.1:8220/downstream/slow?sleepMs=2001"   # 400
curl -s -o /dev/null -w "%{http_code}\n" "http://127.0.0.1:8220/downstream/slow?sleepMs=-1"     # 400
# 400 响应体：code=7005 message=入参校验失败，data.violations=["slow.sleepMs: 最大不能超过2000"]

# 9) URL 层流控：被 Web 拦截器拦下时返回真正的 HTTP 429
curl -s "http://127.0.0.1:8220/system/url-rule?enabled=true"   # 给 /system/probe 加 QPS=2 的 URL 规则
for i in $(seq 1 5); do curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:8220/system/probe; done
# 200 / 200 / 429 / 429 / 429
# 429 响应体：code=7001 blockType=流控规则 FlowException
#   resource=/system/probe（URL 资源名是**纯路径**，不带 GET: 前缀）
curl -s "http://127.0.0.1:8220/system/url-rule?enabled=false"  # 用完删掉
```

**Sentinel Dashboard（可选，本机已实测跑通）**：

```bash
curl --resolve github.com:443:140.82.113.4 -L -o /tmp/sentinel-dashboard.jar \
  https://github.com/alibaba/Sentinel/releases/download/1.8.8/sentinel-dashboard-1.8.8.jar
java -Dserver.port=8080 -Dcsp.sentinel.dashboard.server=127.0.0.1:8080 \
     -Dproject.name=sentinel-dashboard -jar /tmp/sentinel-dashboard.jar &
```

浏览器打开 `http://127.0.0.1:8080`，用 `sentinel / sentinel` 登录，左侧出现 `sentinel-demo` 应用：
「流控规则 / 降级规则 / 热点规则 / 系统规则」页可直接查看并**在线修改**内存中的规则，
「实时监控」页看各资源的 pass/block QPS 曲线。

> dashboard 自己的「客户端命令中心」也监听 8719，与客户端默认端口冲突，
> 所以 `application.yml` 把 `spring.cloud.sentinel.transport.port` 设成了 **8720**。
> 连通性实测：客户端 8720 在监听，dashboard 每 7 秒来拉一次 `/metric`
> （见 `~/logs/csp/command-center.log` 里的 `Socket income: GET /metric?...`）。
> 不启动 dashboard 也完全不影响规则生效——规则在客户端内存里，控制台只是观测/下发通道。

### 实测中发现的一个真实缺陷（务必知道）

**系统保护规则「看起来没生效」，其实是 Sentinel 1.8.6 自身的一个 NPE Bug。** 系统规则默认关闭，
用 `GET /system/enable?enabled=true` 打开、入口 QPS 阈值设为 5，再用 **30 并发 × 60 次**打任意接口：

```bash
curl -s "http://127.0.0.1:8220/system/enable?enabled=true"
seq 1 60 | xargs -P 30 -I@ curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:8220/system/probe | sort | uniq -c
# 实测结果：60 个请求全部返回 200（期望：大多数是 429）
```

但规则**确实在拦**——Sentinel 自己的统计能证明：`__total_inbound_traffic__` 的 `passQps` 被钉在 5.0 不再上升，
`blockQps` 持续上涨，URL 资源 `/system/probe` 出现 `totalPass=5 / totalBlock=55`。
真正的问题是：被拦时 `LogSlot` 抛了 NPE。

```
java.lang.NullPointerException
    at com.alibaba.csp.sentinel.slots.logger.LogSlot.entry(LogSlot.java:41)
```

**根因（已逐行核对 1.8.6 源码）**：`LogSlot.entry` 在 `catch (BlockException e)` 里先记一条 block 日志再 `throw e`：

```java
} catch (BlockException e) {
    EagleEyeLogUtil.log(resourceWrapper.getName(), e.getClass().getSimpleName(),
        e.getRuleLimitApp(), context.getOrigin(), e.getRule().getId(), count);  // ← 第 41 行
    throw e;
}
```

而 `SystemBlockException` 的构造走的是 `super(limitType)`（只传 limitApp 的那个重载），
**从不给父类的 `rule` 字段赋值**，于是 `e.getRule()` 返回 `null`，`e.getRule().getId()` 直接 NPE。
这个 NPE 在 `catch (BlockException e)` 块内抛出，**紧随其后的 `throw e` 就没机会执行**；
它又是 `RuntimeException`，逃出 `AbstractSentinelInterceptor.preHandle` 的 `catch (BlockException)`，
于是既没被当成「限流」处理，也没走 `BlockExceptionHandler` —— 请求就这么正常跑完了，返回 200。
旁证：`~/logs/csp/sentinel-block.log` 里只有 `FlowException`/`ParamFlowException` 记录，
**没有一条 `SystemBlockException` 记录**（那条日志正是坏掉的这一步）。
流控、热点、熔断的异常都通过 `super(limitApp, rule)` 传入了 rule，所以它们不受影响。

**结论与规避**：系统规则的**统计与拦截本身是好的**，坏的是「被拦时写 block 日志」这一步。
所以演示系统保护请用 `/system/entry-node` 看 `passQps` 是否被钉住、`blockQps` 是否上涨来判定生效，
**不要用 HTTP 状态码判断**。生产上若依赖「被拦必须返回 429」，需留意这个 1.8.6 的坑
（升级 Sentinel 版本，或避免让系统规则拦在需要写 block 日志的路径上）。

## 思考点

- 限流阈值怎么定？从容量倒推，而不是猜一个数：单实例压测出的安全 QPS × 0.8 作为阈值，
  再用 `minRequestAmount` 避免低流量误判。阈值太高等于没设，太低会把正常流量拒掉。
- 集群流控：单机阈值 × 实例数 ≠ 集群阈值（流量不均会让部分实例先被限流）。
  Sentinel 提供 `clusterMode` + 独立 token server，代价是多一个要保证高可用的组件，量没到别急着上。
- 规则热更新与持久化：控制台推送的规则重启即丢；接 Nacos 时坚持「客户端只读、控制台负责写」。
- 与网关限流的配合：网关限流挡在入口，按路由/租户维度；应用内的 Sentinel 保护单个接口与整机。
  两层阈值要留余量，否则网关放过的流量全砸在应用上。
