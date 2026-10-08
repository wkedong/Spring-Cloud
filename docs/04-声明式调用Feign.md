# 04 · 声明式调用 Feign（OpenFeign）

> 模块：`service-consumer-feign/`（7020）。
> 版本对照：旧版手写 CommonsMultipartFile 二次包装 → 新版 MultipartFile 直接透传。

## 学什么

RestTemplate 是「命令式」调用：手写 URL、手拼参数。Feign 是「声明式」调用：
把 HTTP 接口定义成 Java 接口，调用时像调本地方法一样自然。

```java
@FeignClient("service-producer")     // 服务名
public interface FeignService {

    @GetMapping("/testFeign")
    String testFeign();

    @PostMapping(value = "/testFile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    String testFeignFile(@RequestPart("file") MultipartFile file);
}
```

加上 `@EnableFeignClients` 后，框架在启动时为接口生成代理：
负载均衡（LoadBalancer）、序列化、超时都内置，服务间调用「变成方法调用」。

## multipart 文件透传（本模块的教学重点）

文件上传走 Feign 需要专门的编码器——`SpringFormEncoder`：

```java
@Configuration
static class MultipartSupportConfig {
    @Bean
    public Encoder feignFormEncoder() {
        return new SpringFormEncoder();   // 让 Feign 支持 multipart/form-data
    }
}
```

## 2021.0.x 的变化（含一处 API 迁移）

| 变化点 | 旧（Edgware） | 新（2021.0） |
| --- | --- | --- |
| starter | `spring-cloud-starter-feign` | `spring-cloud-starter-openfeign` |
| 注解包名 | `org.springframework.cloud.netflix.feign.*` | `org.springframework.cloud.openfeign.*` |
| 负载均衡 | Ribbon | spring-cloud-starter-loadbalancer（同 §03） |
| feign-form | 3.0.3 | 3.8.0（配套 openfeign 3.1.x） |
| 测试文件构造 | `DiskFileItem` + `CommonsMultipartFile`（Spring 5 已移除该类） | `MockMultipartFile` |

> `CommonsMultipartFile` 是本次升级的隐藏地雷：Spring 5 移除了对 commons-fileupload 的包装。
> 老 demo「落盘 → DiskFileItem → CommonsMultipartFile 二次包装」的写法整体作废，
> 新写法直接把原始 `MultipartFile` 交给 Feign 透传——feign-form 3.8 的 SpringFormEncoder
> 原生支持 Spring 的 MultipartFile，代码反而更短了。

## 2025.1 的变化（OpenFeign 3.1.x → 5.0.3，都是「静默失效」型）

**配置前缀整体换家**——旧键**不报错，只是不生效**：

| 旧键（3.1.x，已失效） | 新键（5.0.3） |
| --- | --- |
| `feign.client.config.*` | `spring.cloud.openfeign.client.config.*`（连接/读超时、`logger-level`） |
| `feign.circuitbreaker.enabled` | `spring.cloud.openfeign.circuitbreaker.enabled` |
| `feign.sentinel.enabled` | **不变**（SCA 2025.1.0.0 实测仍是这个键） |

```yaml
spring:
  cloud:
    openfeign:
      circuitbreaker:
        enabled: true            # 打开后 Feign 调用被 CircuitBreaker 包装，fallbackFactory 才生效
      client:
        config:
          default:
            connect-timeout: 2000
            read-timeout: 2000
            logger-level: full
          service-producer:      # 按客户端名覆盖，优先级高于 default
            read-timeout: 2000
```

> **实测对照**：旧键 `feign.client.config.default.read-timeout=2000` 时，5 秒的慢调用**照样跑完**
> （`costMillis≈5167`，无降级）；迁到 `spring.cloud.openfeign.client.config` 后，同一调用
> **约 3.0s 降级**（实测 `costMillis≈3005`、`TimeoutException`，fallbackFactory 兜底）。
> 为什么是 3s 而不是 2s：`read-timeout: 2000` 先触发 RetryableException，客户端 `Retryer.Default(100ms,1s,3)` 开始重试，
> 最终由 Resilience4j 的 **TimeLimiter 3s** 到点掐断——三个数值叠在一起才是你看到的总耗时。

**观测能力要单独引 `feign-micrometer`**（版本由 OpenFeign BOM 管理，不用自己写版本号）：

```xml
<dependency>
    <groupId>io.github.openfeign</groupId>
    <artifactId>feign-micrometer</artifactId>
</dependency>
```

缺它的后果：**没有客户端 span**，而且 **traceId 不会写进下游请求头**，Zipkin 里下游会另起一条新 trace。
另外 feign-form 不再单独指定版本——由 OpenFeign BOM 统一到 13.6.1（与 feign-core 同源）。

> **实测**：修复前 4 个消费者在 Zipkin 里都是**单服务 trace**（producer 侧生成了新 traceId，仅高 32 位相同）；
> 补上 `feign-micrometer`（RestTemplate 侧挂 `ObservationRegistry`）后，各消费者都出现
> `[服务A, service-producer]` 的**同一条 trace**，客户端 span 名为 `http get`。

本模块的**配置中心接入**同样改走 `spring.config.import`（`bootstrap.yml` 与
`spring-cloud-starter-bootstrap` 已移除，见 [02-配置中心](02-配置中心.md)）：

```yaml
spring:
  config:
    import: optional:configserver:http://localhost:6010/
```

## 动手验证

```bash
# 简单调用：
curl http://localhost:7020/testFeign
# → "Hello, Spring Cloud! My port is 6070 This is a testFeign result"

# multipart 透传：
echo demo > /tmp/hello.txt
curl -F "file=@/tmp/hello.txt" http://localhost:7020/testFeignFile
# → 返回文件名 hello.txt（producer 的 testFile 只回显文件名）
```

## 思考点

- Feign 接口 = 服务契约。实践中会把接口 + DTO 抽到独立 `xxx-api` 模块供双方引用，
  避免消费者复制粘贴路径导致契约漂移；
- 超时/重试在 `spring.cloud.openfeign.client.config.default.*` 配置（旧前缀 `feign.client.config.*`
  已静默失效，见上节）；熔断则在 05 章的 Resilience4j 一层做，Feign 侧需打开
  `spring.cloud.openfeign.circuitbreaker.enabled`。
