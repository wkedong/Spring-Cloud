# 04 · 声明式调用 Feign（OpenFeign）

> 模块：`service-consumer-feign/`（7020）。对应旧教程系列同主题（服务消费进阶）。

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
- 超时/重试在 `feign.client.config.default.*` 配置；熔断则在 05 章的 Resilience4j 一层做。
