# 20 · Resilience4j 全家桶：Retry、Bulkhead、RateLimiter、TimeLimiter、CircuitBreaker 与 Decorators
> 模块：`service-consumer-ribbon-hystrix/`（7040）｜前置阅读：docs/05-服务容错保护.md、docs/17-Feign进阶.md

## 学什么

05 篇只讲了熔断（CircuitBreaker）这一个组件，但「弹性」从来不是单个开关。Resilience4j 一共给了六个能力，它们解决的是**不同的问题**，不是同一件事的不同写法：

| 组件 | 解决的问题 | 关键配置 | 本模块实例名 |
| --- | --- | --- | --- |
| `Retry` | 偶发失败（网络抖动）自动再试 | `max-attempts`、`wait-duration`、`retry-exceptions` | `producerRetry` / `decoratedRetry` |
| `Bulkhead` | 限制**同时进行**的调用数，防止一个慢依赖占满线程 | `max-concurrent-calls`、`max-wait-duration` | `producerBulkhead` / `decoratedBulkhead` |
| `RateLimiter` | 限制**单位时间**的调用次数，保护下游配额 | `limit-for-period`、`limit-refresh-period` | `producerRateLimiter` |
| `TimeLimiter` | 给调用设超时上限，到点就走降级 | `timeout-duration`、`cancel-running-future` | `slowCall`（1s） |
| `CircuitBreaker` | 下游持续故障时快速失败，避免拖垮自己 | `sliding-window-size`、`failure-rate-threshold` | `slowCall` / `testHystrix` / `decoratedDemo` |
| `Decorators` | 把上面几个**按顺序**串成一条链 | 顺序即语义，见 §5 | `decoratedDemo` |

最容易混的一对是 Bulkhead 与 RateLimiter：前者限「并发度」（同一瞬间有几个在跑），后者限「频次」（单位时间内总共几次）——下游被瞬时打满用 Bulkhead，下游有配额或需要公平用 RateLimiter。

## 核心代码

### 1. 依赖：starter 少了 Bulkhead 与 Decorators（务必先看）

```text
<dependency>   <!-- 官方 starter：只提供 CircuitBreaker/TimeLimiter 抽象 -->
    <groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-circuitbreaker-resilience4j</artifactId>
</dependency>
<dependency>   <!-- 必须补：Bulkhead 与 Decorators 都在这里；版本由 resilience4j-bom 管理（本仓库 1.7.0） -->
    <groupId>io.github.resilience4j</groupId><artifactId>resilience4j-all</artifactId>
</dependency>
```

只加 starter 会编译报 **「程序包 io.github.resilience4j.bulkhead 不存在」**：`spring-cloud-circuitbreaker-resilience4j` 把 `resilience4j-bulkhead` 声明成了 `optional`（Maven 的 optional 依赖**不会传递**），而 `Decorators` 组合 API 在 `resilience4j-all` 里。`resilience4j-all` 是聚合模块，一次补齐 ratelimiter / circuitbreaker / bulkhead / retry / cache / timelimiter（实测打出来的 jar 里就是 `resilience4j-all-1.7.0.jar` 与 `resilience4j-bulkhead-1.7.0.jar`）。

### 2. Retry：偶发失败自动再试

```java
Retry retry = retryRegistry.retry("producerRetry");              // 名字与 yml 的 instances 对齐
String result = Retry.decorateSupplier(retry, (Supplier<String>) () -> {
    int current = attempt.incrementAndGet();
    if (current < succeedAtAttempt) { throw new IllegalStateException("模拟第 " + current + " 次调用失败"); }
    return "第 " + current + " 次调用成功";                       // 命中 retry-exceptions 才会重试
}).get();
```

### 3. Bulkhead 与 RateLimiter：并发隔离 vs 频次限制

```java
Bulkhead bulkhead = bulkheadRegistry.bulkhead("producerBulkhead");             // max-concurrent-calls=2
String a = Bulkhead.decorateSupplier(bulkhead, () -> { sleep(800L); return "处理完成"; }).get();
RateLimiter limiter = rateLimiterRegistry.rateLimiter("producerRateLimiter");  // 3 次 / 10 秒
String b = RateLimiter.decorateSupplier(limiter, () -> "调用被放行").get();
```

`max-wait-duration: 0` 表示**不排队**：并发槽满了直接抛 `BulkheadFullException`；RateLimiter 的 `timeout-duration: 0` 同理，超配额直接抛 `RequestNotPermitted`，不等下一个周期。

### 4. TimeLimiter：真正的超时靠它，不是靠 HTTP 客户端

```java
String result = circuitBreakerFactory.create("slowCall").run(          // slowCall 配了 1s 超时上限
        () -> restTemplate.getForObject("http://service-producer/testSlow?seconds=" + seconds, String.class),
        throwable -> "【降级】producer 响应超过 1s 上限（" + throwable.getClass().getSimpleName() + "）");
```

Spring Cloud CircuitBreaker 的实现已把 TimeLimiter 集成进来，所以 `create("slowCall")` 会同时按 `resilience4j.timelimiter.instances.slowCall` 与 `resilience4j.circuitbreaker.instances.slowCall` 生效。

### 5. Decorators：组合顺序就是语义

```java
Supplier<String> decorated = Decorators.ofSupplier(business)
        .withRetry(retry)                    // 最内层：被重试的是业务本身
        .withCircuitBreaker(circuitBreaker)  // 熔断器统计的是「重试前的每一次尝试」
        .withBulkhead(bulkhead)              // 最外层：先决定放不放你进来
        .withFallback(t -> "【兜底】" + t.getClass().getSimpleName() + "：" + t.getMessage())
        .decorate();
```
越靠前越「内层」：Bulkhead 决定放行 → CircuitBreaker 判断是否短路 → Retry 在内部重复执行 → 全都失败才走 Fallback。

### 6. bootstrap.yml：各组件配置

```yaml
resilience4j:
  retry:
    configs.default: { max-attempts: 3, wait-duration: 200ms, retry-exceptions: [java.lang.IllegalStateException, org.springframework.web.client.ResourceAccessException] }
    instances.decoratedRetry: { max-attempts: 2, wait-duration: 100ms }
  bulkhead:
    configs.default: { max-concurrent-calls: 2, max-wait-duration: 0 }   # 0 = 不等待，满了直接拒绝
    instances.decoratedBulkhead: { max-concurrent-calls: 5 }
  ratelimiter:
    configs.default: { limit-for-period: 3, limit-refresh-period: 10s, timeout-duration: 0 }
  timelimiter:
    configs.default: { timeout-duration: 3s, cancel-running-future: true }
    instances.slowCall: { timeout-duration: 1s, cancel-running-future: true }   # instances 覆盖 configs
  circuitbreaker:
    configs.default: { sliding-window-type: COUNT_BASED, sliding-window-size: 10, failure-rate-threshold: 50, wait-duration-in-open-state: 10s }
    instances.slowCall: { sliding-window-size: 10, minimum-number-of-calls: 3, failure-rate-threshold: 60, wait-duration-in-open-state: 5s }
    instances.decoratedDemo: { sliding-window-size: 10, minimum-number-of-calls: 5, failure-rate-threshold: 50, wait-duration-in-open-state: 5s }
```

## 关键机制与易错点

1. **依赖坑（最常见）**：`spring-cloud-starter-circuitbreaker-resilience4j` **不含** bulkhead 与 Decorators，必须额外加 `io.github.resilience4j:resilience4j-all`，否则编译期就报「程序包 io.github.resilience4j.bulkhead 不存在」；版本不用手写，`resilience4j-bom` 已经管好（本仓库解析为 1.7.0）。
2. **TimeLimiter 的默认值是 1s**：不改就直接打断正常业务；本模块特意用 `configs.default: 3s` 兜底、只为 `slowCall` 实例单独配 1s，`instances` 优先于 `configs`，两层都配时要确认到底命中了哪个。
3. **Bulkhead 的 `max-wait-duration: 0` 是不等待**：并发满了立刻 `BulkheadFullException`，不是「排 0 秒队」；要缓解尖峰就给它一个正的等待时长，但那会把压力转成上游线程的等待。
4. **Retry 与 CircuitBreaker 的顺序别颠倒**：必须是 Retry 在内、CircuitBreaker 在外（`.withRetry().withCircuitBreaker()`）。反过来的话熔断器统计到的是**重试之后**的最终结果，失败次数被压缩、打开时机被推迟；Retry 在内层时每一次失败尝试都会被计入统计。
5. **同步调用才能用 TimeLimiter**：它本质是把调用扔到独立线程池里限时等待，所以不能拿它去包已经异步的 `CompletableFuture` 链，否则白占一个线程池；`cancel-running-future: true` 只是尽力中断，底层阻塞的 IO 未必真的停。
6. **熔断器名字要对齐两处**：`circuitBreakerFactory.create("名字")` 与 `resilience4j.circuitbreaker.instances.<名字>` 必须一致，否则拿到的是 `default` 配置——「明明配了阈值却不生效」基本都是这里。`feign.circuitbreaker.enabled=true` 走的是同一套：Feign 的每个方法都会被包成一个以方法签名命名的熔断器（呼应 docs/17，那边 `/actuator/circuitbreakers` 里能看到 `FeignService#testError()` 这类名字）。
7. **actuator 端点不会自动出现**：`/actuator/circuitbreakers`、`circuitbreakerevents`、`retries`、`bulkheads`、`ratelimiters`、`timelimiters` 都需要在 `management.endpoints.web.exposure.include` 里显式放开。`failureRate` 显示 **-1.0** 不是「失败率是负的」，而是样本不足、还没算出来。
8. **重试的前提是幂等**：GET 可以随便重试，写操作重试必须有幂等键，否则一次超时可能变成两笔订单；叠加网关的 Retry（docs/19）时，两层重试的次数是相乘的。
9. **熔断器是「慢慢打开」的**：`minimum-number-of-calls: 5` + `failure-rate-threshold: 50` 意味着样本没攒够就不会开——所以 `/resilience/decorators?fail=true` 返回的 `circuitBreakerState` 取决于之前累积了多少次调用，单跑一次可能还是 `CLOSED`。

## 动手验证

```bash
# ① 各组件实时状态：等价于 Hystrix Dashboard 想做的事，但更细
curl -s http://127.0.0.1:7040/resilience/status | python3 -m json.tool
# {"retry.producerRetry":{"successfulCallsWithRetry":2,...,"maxAttempts":3},"bulkhead.producerBulkhead":{"maxConcurrentCalls":2,"availableConcurrentCalls":2,"maxWaitDuration":"PT0S"},
#  "ratelimiter.producerRateLimiter":{"availablePermissions":3,"limitForPeriod":3,"numberOfWaitingThreads":0},
#  "circuitBreakers":{"slowCall":{"state":"CLOSED","failureRate":-1.0,"numberOfBufferedCalls":2,"numberOfFailedCalls":2}, ...}}
# 期望看到什么：重试统计（含 maxAttempts）、剩余并发/配额、每个熔断器的 state/failureRate/缓冲调用数一次看全；maxWaitDuration 是 "PT0S" 即 max-wait-duration: 0 的效果；failureRate=-1.0 表示样本不足，不是异常值。

# ② Retry：前 2 次故意失败、第 3 次成功
curl -s http://127.0.0.1:7040/resilience/retry
# {"success":true,"result":"第 3 次调用成功","attempts":3,"metrics":{"successfulCallsWithRetry":3,"successfulCallsWithoutRetry":0,"maxAttempts":3}}
# 期望看到什么：attempts=3 且 success=true，重试把偶发失败「抹平」了；successfulCallsWithRetry 累加说明这次成功靠的是重试；/resilience/retry?succeedAtAttempt=5 会因超过 max-attempts=3 而失败。

# ③ Bulkhead：并发 5 个，只放行 2 个
curl -s http://127.0.0.1:7040/resilience/bulkhead
# {"concurrency":5,"permitted":2,"rejected":3,"name":"producerBulkhead","maxConcurrentCalls":2,"availableConcurrentCalls":2,
#  "results":[{"call":0,"permitted":true,"result":"处理完成","costMillis":804},{"call":1,"permitted":true,"result":"处理完成","costMillis":805},
#             {"call":2,"permitted":false,"exceptionType":"BulkheadFullException","costMillis":0}, ...（call 3、4 同样被拒）]}
# 期望看到什么：permitted=2 / rejected=3；被放行的两个 costMillis≈804/805（业务 sleep 800ms）说明它们**真的并行**在跑（串行会接近 1600）；被拒的三个 costMillis=0，因为 max-wait-duration=0 直接抛异常，一毫秒都没等。

# ④ RateLimiter：配额 3 次/10 秒，连打 5 次
for i in $(seq 1 5); do curl -s http://127.0.0.1:7040/resilience/ratelimiter | \
  python3 -c "import sys,json;d=json.load(sys.stdin);print('permitted=%s available=%s %s' % (d['permitted'], d['availablePermissions'], d.get('message','')))"; done
# permitted=True available=2 / permitted=True available=1 / permitted=True available=0
# permitted=False available=0 限流器拒绝：配额已用尽，请稍后重试（第 5 次同样被拒）
# 期望看到什么：availablePermissions 从 2 递减到 0，第 4、5 次 permitted=false；available 从「剩余 2」开始显示，是因为本次调用已经消耗掉 1 个名额；等 10 秒周期刷新后再打又能放行。

# ⑤ TimeLimiter：下游 3 秒、超时上限 1 秒 → 提前降级
curl -s http://127.0.0.1:7040/resilience/timelimiter
# {"seconds":3,"result":"【降级】producer 响应超过 1s 上限（TimeoutException）","costMillis":1012,"hint":"costMillis 应接近 1000 而不是 3000"}
# 期望看到什么：costMillis≈1012 而不是 3000 —— 1 秒就到点走 fallback 了；去掉 TimeLimiter（或把 slowCall 的超时调大）再试，costMillis 会变成 3000 左右。

# ⑥ Decorators 组合：正常路径 vs 业务失败
curl -s "http://127.0.0.1:7040/resilience/decorators?fail=false"
# {"fail":false,"result":"第 1 次调用成功","attempts":1,"circuitBreakerState":"CLOSED"}
curl -s "http://127.0.0.1:7040/resilience/decorators?fail=true"
# {"fail":true,"result":"【兜底】IllegalStateException：模拟业务失败（第 2 次）","attempts":2,"circuitBreakerState":"OPEN"}
# 期望看到什么：正常时 attempts=1（重试没白跑）；失败时 attempts=2（decoratedRetry 的 max-attempts=2，重试了一次），兜底文案带的是**最后一次**的异常信息——这正是「Retry 在内层」的证据：重试发生在兜底之前。circuitBreakerState 是否 OPEN 取决于历史累积，单跑一次很可能还是 CLOSED。

# ⑦ actuator 暴露的弹性组件端点；⑧ 熔断器事件：超时是被谁记下来的
for e in retries bulkheads ratelimiters timelimiters circuitbreakers; do \
  printf '%-16s ' $e; curl -s -o /dev/null -w 'HTTP %{http_code}\n' http://127.0.0.1:7040/actuator/$e; done
# retries 200 / bulkheads 200 / ratelimiters 200 / timelimiters 200 / circuitbreakers 200
curl -s 'http://127.0.0.1:7040/actuator/circuitbreakerevents' | head -c 300
# {"circuitBreakerEvents":[{"circuitBreakerName":"slowCall","type":"ERROR","errorMessage":"java.util.concurrent.TimeoutException: TimeLimiter 'slowCall' recorded a timeout exception.","durationInMs":1005,"stateTransition":null}, ...]}
# 期望看到什么：五个端点全部 200（需要 exposure.include 放开），任意一个 404 都是没 expose、不是组件没生效；slowCall 上的 ERROR 事件由 TimeLimiter 记入（durationInMs≈1005，即 1s 上限），说明超时最终是以「熔断器的一次失败」被统计的——这就是超时阈值要和熔断阈值一起调的原因。
# 顺带确认向下兼容：curl -s http://127.0.0.1:7040/testHystrix 仍返回 service-producer /testHystrix is error。
```

## 思考点

1. Bulkhead 与 RateLimiter 都叫「限流」，什么场景必须用 Bulkhead 而不能用 RateLimiter？反过来呢？
2. 为什么 Retry 要在 CircuitBreaker **内层**？如果把顺序反过来，一次调用产生的失败次数分别如何进入熔断器统计，哪一种会更快打开熔断？
3. TimeLimiter 需要独立线程池，这对线程资源意味着什么？高并发下 `timeout-duration` 配得过小会把「慢」变成「错」，你会怎么定这个值？
4. `decorators?fail=true` 的兜底文案里带的是「第 2 次」的异常。如果加 `withFallback` 之前忘了 `withRetry`，这个数字会变成几？为什么？
5. 本模块的 `Retry` 与网关的 `Retry`（docs/19）叠加时，下游最多会被调用几次？这种「双层重试」在什么情况下是危险的？
6. `failureRate` 显示 -1.0、`minimum-number-of-calls: 5` 这两个细节，说明熔断器的统计口径是什么？生产阈值该怎么按流量规模来定？
