# 17 · Feign 进阶：请求头、日志、错误解码、超时与降级

> 模块：`service-consumer-feign/`（7020）。前置阅读：[04-声明式调用Feign](04-声明式调用Feign.md)、[05-服务容错保护](05-服务容错保护.md)。本篇输出均来自 `/tmp/up-smoke/logs/service-consumer-feign.log` 的实测记录。

## 学什么

04 篇解决了「怎么把 HTTP 调用写成一个接口」；接口一旦真上生产，问题会集中爆在这几处：

| 生产问题 | 本篇机制 | 本模块实现 |
| --- | --- | --- |
| 下游不知道请求是谁发的、灰度标记断了 | `RequestInterceptor` | `interceptor/BusinessHeaderInterceptor.java` |
| 出错只能靠猜，看不到实际请求/响应 | `Logger.Level` + 日志级别 | `config/ProducerFeignConfig.java` |
| 下游 500 一律是裸 `FeignException`，无法按语义处理 | 自定义 `ErrorDecoder` | `exception/DownstreamServiceException.java`、`NotFoundException.java` |
| 下游变慢拖死上游 | `connectTimeout`/`readTimeout` + `Retryer` | `application.yml` 的 `spring.cloud.openfeign.client.config.*` |
| 下游故障时整体不可用 | `FallbackFactory` + `spring.cloud.openfeign.circuitbreaker.enabled` | `service/ProducerFallbackFactory.java` |
| 跨服务 traceId 断了（Zipkin 里下游另起一条 trace） | `feign-micrometer` 观测集成 + W3C 传播头 | `pom.xml` 的 `io.github.openfeign:feign-micrometer` |

## 核心代码

### 1. 请求头拦截器：横切加工，而不是在业务里零散拼 header

```java
public class BusinessHeaderInterceptor implements RequestInterceptor {
    @Override
    public void apply(RequestTemplate template) {
        template.header("X-From", "service-consumer-feign");                        // ① 调用方标识
        template.header("X-Request-Id", UUID.randomUUID().toString()
                .replace("-", "").substring(0, 16));                               // ② 业务请求号
        HttpServletRequest request = currentRequest();
        if (request != null && request.getHeader("X-Gray-Version") != null) {
            template.header("X-Gray-Version", request.getHeader("X-Gray-Version")); // ③ 透传灰度标记
        }
        String traceId = MDC.get("traceId");
        if (traceId != null) { template.header("X-MDC-Trace-Id", traceId); }        // ④ MDC 兜底
    }
}
```

源码注释里那句「**不要**在这里手动塞链路头」是重点：升级到 Micrometer Tracing 后，链路头由 **`feign-micrometer`**（OpenFeign 的观测集成，版本由 OpenFeign BOM 管理）自动注入 **W3C 的 `traceparent`**，你再注一遍就是重复头。注意 `feign-micrometer` 是**必须显式引入**的依赖：实测缺它时没有客户端 span、traceId 也不会写进下游请求头，Zipkin 里下游会另起一条新 trace。`currentRequest()` 走 `RequestContextHolder`（ThreadLocal），只在处理 HTTP 请求的线程里有值——`@Async` 线程、定时任务里发起的调用它必然是 `null`。

### 2. 日志级别：`@Bean` 决定「能不能打」，日志级别决定「打不打」

```java
@Bean
public Logger.Level feignLoggerLevel() {
    return Logger.Level.FULL;   // NONE / BASIC（方法+URL+状态码+耗时）/ HEADERS / FULL（含 body）
}
```
```yaml
spring.cloud.openfeign.client.config.default.logger-level: full              # 或像本模块一样用上面的 @Bean
logging.level.com.wkedong.springcloud.serviceconsumer.feign.service: debug   # 没这行，一条都不打
```
日志级别要配**客户端接口所在包**（Feign 按接口全限定类名取 logger），配 `feign` 包或漏配都看不到输出。

### 3. 自定义 ErrorDecoder：把 HTTP 状态码翻译成业务语义异常

```java
@Bean
public ErrorDecoder producerErrorDecoder() {
    final ErrorDecoder defaultDecoder = new ErrorDecoder.Default();
    return (methodKey, response) -> {
        String body = readBody(response);                                             // 只读前 512 字节
        log.warn("下游返回非 2xx：methodKey={}, status={}, body={}", methodKey, response.status(), body);
        if (response.status() == 404) { return new NotFoundException("下游资源不存在：" + methodKey); }
        if (response.status() >= 500) { return new DownstreamServiceException("下游服务错误(" + response.status() + ")：" + body); }
        return defaultDecoder.decode(methodKey, response);                            // 其它状态码交回默认实现
    };
}
```
调用方从此可以 `catch (DownstreamServiceException e)`，而不用 `catch (FeignException e)` 再对着 `status()` 猜含义。默认实现把**一切非 2xx** 都抛成 `FeignException` 子类（404 → `FeignException$NotFound`、500 → `FeignException$InternalServerError`）。

### 4. 超时与重试：两个独立开关，但预算会互相吃掉

```yaml
spring:
  cloud:
    openfeign:
      circuitbreaker:
        enabled: true            # 开启后所有 Feign 调用被 CircuitBreaker 包装，fallbackFactory 才生效
      client:
        config:
          default:               # 全局默认
            connect-timeout: 2000 # 单位毫秒（不是 Spring 常见的 Duration 写法）
            read-timeout: 2000    # 下游 /testSlow?seconds=5 必然读超时
          service-producer:      # 按客户端（contextId）覆盖，优先级高于 default
            connect-timeout: 2000
            read-timeout: 2000
```
```java
@Bean
public Retryer feignRetryer() { return new Retryer.Default(100L, 1000L, 3); }   // 间隔 100ms 起、上限 1s、最多 3 次
```

### 5. 降级工厂：拿得到 cause，才能区分「超时 / 下游 500 / 熔断打开」

```java
@Component                                     // 必须是 Spring Bean，Feign 要在容器里找到它
public class ProducerFallbackFactory implements FallbackFactory<ProducerFallbackClient> {
    @Override
    public ProducerFallbackClient create(Throwable cause) {
        logger.warn("服务降级触发：producer 调用失败，原因类型={}, 原因={}",
                cause.getClass().getSimpleName(), cause.getMessage());
        return new ProducerFallbackClient() { /* 按方法返回兜底文案 */ };
    }
}
```
`@FeignClient(name = "service-producer", contextId = "producerFallback", fallbackFactory = ...)`：同一服务名可以有多个客户端接口，**必须用 `contextId` 区分**，否则抛 "Multiple Feign Clients with the same name..."。

## 关键机制与易错点

1. **异常类型由 ErrorDecoder 决定，是否降级由 CircuitBreaker 决定**——这两件事经常被混为一谈。本模块特意做了一组对照：两个客户端打同一个下游 500 接口，`FeignService` 配了自定义 ErrorDecoder ⇒ 兜底文案是 `DownstreamServiceException`；`ProducerFallbackClient` 没配 ⇒ 同样的下游 500 拿到 `InternalServerError`（`FeignException`）。降级**都发生了**（两个都配了 fallbackFactory），差别只在异常类型。

2. **`spring.cloud.openfeign.circuitbreaker.enabled=true` 之后所有 Feign 调用都被熔断器包了一层**。副作用是：没有配 fallback 的客户端失败时抛 `NoFallbackAvailableException`（内部包着原始异常）。它不是业务异常，排错时别被这个名字带偏——它只是在说「熔断器触发了，但你没给兜底」。这条配置同时也是 `fallbackFactory` 生效的前提。

3. **重试 × 超时 × 熔断预算要一起算**——本篇最值钱的一条。实测链路：`readTimeout=2s` 触发后被 `Retryer.Default(100,1000,3)` 当成可重试异常接手，理论最坏 3×2s=6s；而 Resilience4j `TimeLimiter` 的 `timeout-duration: 3s` 预算只有 3s，于是它先动手掐断，`costMillis` 稳定在 **3002~3005**（复核实测 3002 / 3003 / 3005）。结论：`readTimeout × 重试次数 < TimeLimiter 预算`，否则你看到的永远是熔断器的超时，永远看不到「Feign 自己重试了几次」，用户还要等满整个预算。

4. **`spring.cloud.openfeign.client.config.default.*` 与 `spring.cloud.openfeign.client.config.<名>.*`**：前者全局默认，后者按客户端覆盖，这里的 `<名>` 严格说是 **contextId**（不写时默认等于 `name`）。**升级必改**：OpenFeign 5.0.3 已不认旧前缀 `feign.client.config.*`，而旧键**不会报错、只是静默失效**——实测把 `read-timeout=2000` 写在旧键上时，5 秒的慢调用照样跑完（`costMillis≈5167`，**无降级**）；迁到 `spring.cloud.openfeign.client.config` 后 readTimeout 立即生效，同一调用会在 TimeLimiter 预算内提前降级（cause=`TimeoutException`，fallbackFactory 兜底）。本模块 `service-producer` 那段对 `ProducerFallbackClient`（`contextId=producerFallback`）**不匹配**，它只吃到 `default`——两处都配成 2000 所以看不出差别，但这是真实存在的坑。优先级上 `defaultToProperties` 默认 `true`：Java 配置类先应用、properties 后应用并覆盖它。

5. **actuator 端点默认只暴露 health/info**。`/actuator/circuitbreakers`、`/actuator/circuitbreakerevents` 必须显式加进 `management.endpoints.web.exposure.include`，否则直接 **404**（实测踩过，极易误判成「熔断器没生效」）。另外 `readBody` 只读前 512 字节：错误信息留摘要即可，把整个下游响应体塞进异常既费内存也容易泄敏。

## 动手验证

服务已在运行（7020 feign、6070/6080 producer），以下均为只读命令。

```bash
# ① 请求头拦截器：下游回显收到的头
curl -s http://127.0.0.1:7020/testFeignHeaderEcho
# highlight 里：X-From=service-consumer-feign、X-Request-Id=16 位十六进制；链路头由 feign-micrometer / Micrometer Tracing 注入（W3C 的 traceparent）
# 原样头含 x-mdc-trace-id、traceparent: 00-<traceId>-<spanId>-01；没有 x-gray-version（上游没带灰度头，走不到拦截器分支③）
# ② FULL 级别日志：请求头、响应头、整个响应体（`--->`/`<---`、END HTTP、200 (15ms) 是 FULL 独有的骨架）
grep -E 'testFeignHeaderEcho|echoHeaders' /tmp/up-smoke/logs/service-consumer-feign.log | tail -12
# DEBUG ... c.w.s.s.feign.service.FeignService : [FeignService#echoHeaders] X-Request-Id: 808db0c299754b5b
# DEBUG ... [FeignService#echoHeaders] ---> END HTTP (0-byte body)
# DEBUG ... [FeignService#echoHeaders] <--- HTTP/1.1 200 (15ms)
# DEBUG ... [FeignService#echoHeaders] {"port":6070,"instanceId":"192.168.85.52:service-producer:6070",...}
# DEBUG ... [FeignService#echoHeaders] <--- END HTTP (647-byte body)

# ③ ErrorDecoder 与对照组：同样的下游 500，异常类型不同（降级都触发了）
curl -s http://127.0.0.1:7020/testFeignErrorDecode
# {"success":true,"result":"【降级】testError 调用失败，原因类型=DownstreamServiceException（FeignService fallbackFactory 兜底）"}
curl -s http://127.0.0.1:7020/testFeignFallback
# 【降级】producer /testError 调用失败：InternalServerError（由 FallbackFactory 兜底）

# ④ ErrorDecoder 与降级的日志证据
grep -E '降级触发|下游返回非 2xx' /tmp/up-smoke/logs/service-consumer-feign.log | tail -3
# WARN c.w.s.s.f.config.ProducerFeignConfig : 下游返回非 2xx：methodKey=FeignService#testError(), status=500, body={"error":"PRODUCER_INTERNAL_ERROR","code":50001,...}
# WARN c.w.s.s.f.s.FeignServiceFallbackFactory : FeignService 降级触发：cause=java.util.concurrent.TimeoutException: TimeLimiter 'FeignServicetestSlowint' recorded a timeout exception.

# ⑤ 超时 × 重试 × 熔断预算：读超时 2s，却稳定在 3s 左右返回
curl -s http://127.0.0.1:7020/testFeignTimeout
# {"success":true,"result":"【降级】testSlow?seconds=5 ... 原因类型=TimeoutException（...）","costMillis":3003}
curl -s http://127.0.0.1:7020/testFeignFallbackTimeout   # 【降级】producer /testSlow?seconds=5 调用失败：RetryableException（由 FallbackFactory 兜底）
# 期望 costMillis 落在 3002~3005：既不是 2000（Feign 读超时）也不是 6000（3 次重试打满），
# 因为 TimeoutException 来自 Resilience4j TimeLimiter——重试被熔断预算吃掉了。
# 把 resilience4j.timelimiter.configs.default.timeout-duration 调到 10s，才会看到 Feign 层重试满 3 次的约 6s 行为。

# ⑥ 熔断器确实包住了每一个 Feign 方法（一个方法一个熔断器；2025.1 起实例名不再带 # /括号，
#    而是「接口名+方法名+参数类型」直接拼接——实测降级日志里的 TimeLimiter 实例名就是 FeignServicetestSlowint）
curl -s http://127.0.0.1:7020/actuator/circuitbreakers
# circuitBreakers 里按上面的拼接规则能找到每个方法对应的实例，如 FeignServiceechoHeaders、FeignServicetestSlowint
# ⑦ 拦截器的灰度透传分支：带上灰度头，下游回显里应出现 x-gray-version
curl -s -H 'X-Gray-Version: v2' http://127.0.0.1:7020/testFeignHeaderEcho | grep -o 'x-gray-version[^,]*'
```
> 复现方式：按 [docs/09](09-本地运行指南.md) 启动 eureka → config → service-producer → service-consumer-feign，再执行上面的命令；
> 想看调试日志就把启动输出重定向到文件（示例 `/tmp/up-smoke/logs/service-consumer-feign.log`）再 grep，本篇引用的日志片段都来自这个文件。

## 思考点

1. 为什么 Feign 的 `Retryer` 与 CircuitBreaker 的 `TimeLimiter` 会互相影响？`readTimeout=2s` × 重试 3 次、`TimeLimiter=3s` 时用户实际等多久？把重试关掉（`Retryer.NEVER_RETRY`）后，这套预算该怎么重排？
2. 既然「异常类型由 ErrorDecoder 决定、是否降级由 CircuitBreaker 决定」，那在 ErrorDecoder 里把 500 翻译成一个普通 `RuntimeException`，降级还会不会触发？熔断器失败率统计的又是哪一个？
3. `RequestInterceptor` 从 `RequestContextHolder` 取灰度头，在 `@Async`/定时任务里必然拿不到。要让灰度标记在非 HTTP 入口也生效，有哪些做法？代价是什么？
4. `Logger.Level.FULL` 会把请求体和响应体都写进日志。生产该选哪一级？要「默认 BASIC、排障时临时给某个客户端开 FULL 而不重启」，你会怎么做？
5. 同一服务名下挂了两个客户端接口（`FeignService` 与 `ProducerFallbackClient`），为什么必须用 `contextId` 区分？`spring.cloud.openfeign.client.config` 里的 key 按哪个名字找？
6. 把 404 翻译成 `NotFoundException` 后，「查不到就返回空列表」看起来很优雅——什么情况下它会把下游的路径写错（404）伪装成正常的业务空结果？
