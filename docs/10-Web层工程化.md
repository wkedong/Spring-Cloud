# 10 · Web 层工程化：统一响应、全局异常、参数校验、过滤器与拦截器

> 模块：`springboot-basics/`（8010，单机可跑，H2 内存库）。
> 前置：JDK 8+。本模块**不依赖**注册中心/配置中心/数据库中间件，`java -jar` 即可运行。

## 学什么

前面 01~09 讲的是「服务之间怎么协作」，本篇开始讲**每个服务自己该长什么样**。
一个接口层如果没有统一的规矩，代码会迅速退化成这样：

```java
@GetMapping("/user")
public Object getUser(Long id) {
    if (id == null) return "id 不能为空";           // 字符串
    User user = service.find(id);
    if (user == null) return new HashMap<>().put("msg", "用户不存在"); // Map
    return user;                                    // 直接裸对象
}
```

调用方要为每个接口写一套解析分支，出错时既没有错误码也没有线索。本篇解决四件事：

| 问题 | 方案 | 本模块的实现 |
| --- | --- | --- |
| 返回结构五花八门 | 统一响应体（业务码 + 数据 + traceId） | `web/ApiResponse.java` |
| 异常处理散落各处、堆栈泄露给前台 | 全局异常处理（分层捕获 + 兜底） | `web/GlobalExceptionHandler.java` |
| 参数校验靠 if-else | Bean Validation（JSR-303）+ 自定义注解 | `web/dto/UserCreateRequest.java`、`validation/ChineseMobile.java` |
| 横切逻辑（鉴权、追踪、耗时）侵入业务 | 过滤器 + 拦截器 | `web/TraceIdFilter.java`、`web/AuthInterceptor.java` |

## 核心代码

### 1. 统一响应体：把「协议层」和「业务层」分开

```java
public class ApiResponse<T> {
    private int code;        // 业务码：0 成功
    private String message;
    private T data;
    private String traceId;  // 由 TraceIdFilter 写入 MDC，自动带进每个响应
    private long timestamp;
}
```

`code` 属于业务语义，HTTP 状态码属于协议语义，两者**不要互相代替**：网关/监控/告警看状态码，
业务方看 `code`。`traceId` 是排障的关键——用户截图里带着它，你就能在日志里捞出整条链路。

### 2. 全局异常处理：可预期的精确处理，不可预期的兜底

```java
@ExceptionHandler(BusinessException.class)                  // 业务异常 → 按业务码映射状态码
public ResponseEntity<ApiResponse<Map<String, Object>>> handleBusiness(...) 

@ExceptionHandler(MethodArgumentNotValidException.class)     // 校验失败 → 字段级错误
@ExceptionHandler(ConstraintViolationException.class)        // 方法参数校验失败
@ExceptionHandler({MissingServletRequestParameterException.class, ...}) // 请求格式错误
@ExceptionHandler(Exception.class)                           // 兜底：固定文案 + ERROR 日志
```

| 异常 | HTTP | 业务码 | 返回给前台 |
| --- | --- | --- | --- |
| `BusinessException`（未认证） | 401 | 40100 | 异常原文 |
| `BusinessException`（资源不存在） | 404 | 40400 | 异常原文 |
| `BusinessException`（一般业务失败） | 400 | 50000 | 异常原文 |
| 参数校验失败 | 400 | 40000 | **字段名 → 校验消息** 的 Map |
| 未预期异常 | 500 | 50001 | 只有固定文案 + traceId，堆栈只进日志 |

业务码 → 状态码的映射写在一处（`toHttpStatus`），避免「同一个意思有时 400 有时 401」。

### 3. 参数校验：声明式，且失败信息要能直接喂给前端

```java
public class UserCreateRequest {
    @NotBlank(message = "姓名不能为空") @Size(max = 20, ...) private String name;
    @Email(message = "邮箱格式不正确")                        private String email;
    @ChineseMobile(message = "手机号格式不正确")               private String phone;
    @Min(value = 1, ...) @Max(value = 150, ...)              private Integer age;
}
```

常用注解速记：`@NotNull`（非 null）、`@NotBlank`（字符串非空白）、`@NotEmpty`（集合/数组非空）、
`@Size`、`@Min`/`@Max`、`@Email`、`@Pattern`。自定义注解三要素：`@Constraint(validatedBy=...)` +
`message()` 默认值 + `groups()`/`payload()`（规范要求必须声明）。校验器要**无状态、快**，
查库/调远程这类校验放 Service 层。

> ⚠️ 方法参数级校验（如 `@PathVariable @Min(1)`）必须在**类上加 `@Validated`** 才生效，
> 否则注解会被静默忽略——这是最常见的「校验没生效」原因之一。

### 4. 过滤器与拦截器：同一件事的两种切面

| | Filter（Servlet 规范） | Interceptor（Spring MVC） |
| --- | --- | --- |
| 归属 | Servlet 容器 | Spring 容器 |
| 范围 | 所有请求（含静态资源） | 只拦 DispatcherServlet 处理的请求 |
| 依赖注入 | 只能拿到容器引用 | 天然支持 |
| 能否拿到 controller 方法 | 不能 | 能（`HandlerMethod`，可读注解） |
| 本模块用途 | `TraceIdFilter`：traceId 写 MDC + 回写响应头 | `AuthInterceptor`：按路径鉴权、记录耗时 |

`TraceIdFilter` 用 `OncePerRequestFilter` 而不是裸 `Filter`（避免 forward/include 重复执行），
并在 `finally` 里 `MDC.remove`——Web 容器复用线程，不清会串号。
`AuthInterceptor` 鉴权失败时**抛异常**而不是自己写响应，交给全局异常处理器统一输出结构。

## 关键机制与易错点

1. **MDC 是线程绑定的**：日志模板里 `%X{traceId:-no-trace}` 能自动带出，但 `@Async`/线程池里的日志
   不会继承 MDC，需要手动 `MDC.setContextMap(...)` 传递（见 docs/13 的异步章节）。
2. **兜底 handler 不能透传 `e.getMessage()`**：既泄露实现细节（表名、SQL、路径），也无法指导用户；
   正确做法是固定文案 + traceId，真实原因进日志。
3. **@RestControllerAdvice 不会吞掉框架语义**：404/405 由 Spring MVC 自己处理，除非显式捕获；
   需要统一 404 结构时再单独加 `NoHandlerFoundException` 处理并打开
   `spring.mvc.throw-exception-if-no-handler-found`。
4. **校验的位置选择**：格式类校验放 DTO（无副作用、可复用），业务规则类校验（唯一性、余额充足）
   放 Service，并抛 `BusinessException`。
5. **跨域（CORS）**：本模块在 `WebMvcConfig#addCorsMappings` 里配了 `/api/**` 的跨域；
   预检请求 `OPTIONS` 必须在鉴权拦截器里放行，否则浏览器侧永远失败。

## 动手验证

先启动（本模块自带 H2，无需准备任何中间件）：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
cd Spring-Cloud
mvn -pl springboot-basics -am package -DskipTests
java -jar springboot-basics/target/springboot-basics-0.0.1-SNAPSHOT.jar
```

演示用 token 是 `dev-token`（配置项 `basics.security-token`）。

```bash
# ① 鉴权：不带 token → 401 + 业务码 40100 + traceId
curl -i http://127.0.0.1:8010/api/users
# HTTP 401
# {"code":40100,"message":"未认证：请在请求头携带 X-Token","data":{"uri":"/api/users"},
#  "traceId":"e2ef6ba590144adc","timestamp":...}

# ② 白名单接口免鉴权（拦截器 excludePathPatterns）
curl -s http://127.0.0.1:8010/api/public/ping
# {"code":0,...,"data":{"pong":true,"note":"本接口在拦截器白名单内，无需 X-Token"},...}

# ③ 参数校验失败：一次返回全部字段级错误
curl -s -X POST -H 'X-Token: dev-token' -H 'Content-Type: application/json' \
  -d '{"name":"","email":"bad-email","phone":"12345","age":0}' http://127.0.0.1:8010/api/users
# {"code":40000,"message":"参数校验失败",
#  "data":{"email":"邮箱格式不正确","name":"姓名不能为空","age":"年龄必须大于 0","phone":"手机号格式不正确"}}

# ④ 校验通过 → 统一成功结构
curl -s -X POST -H 'X-Token: dev-token' -H 'Content-Type: application/json' \
  -d '{"name":"王五","email":"wangwu@example.com","phone":"13800138000","age":30}' http://127.0.0.1:8010/api/users
# {"code":0,"message":"success","data":{"id":100,"name":"王五",...},"traceId":"f87e136028374a28",...}

# ⑤ 业务异常 vs 系统异常：状态码与文案的区别
curl -s -w '\nHTTP %{http_code}\n' -H 'X-Token: dev-token' http://127.0.0.1:8010/api/demo/business-error
# {"code":50000,"message":"演示用业务异常：余额不足",...}   HTTP 400
curl -s -w '\nHTTP %{http_code}\n' -H 'X-Token: dev-token' http://127.0.0.1:8010/api/demo/system-error
# {"code":50001,"message":"服务器内部错误，请联系管理员并提供 traceId",...}   HTTP 500
```

**同一次请求的 traceId 会出现在响应体、响应头与全部日志中**（实测）：

```
响应体 traceId：e2ef6ba590144adc
响应头：X-Request-Id: e2ef6ba590144adc
日志：14:43:11.098 WARN [http-nio-8010-exec-2] [e2ef6ba590144adc] c.w.s.basics.web.AuthInterceptor
        - 鉴权失败：uri=/api/users, 期望请求头 X-Token
```

系统异常的堆栈**只在日志里**，前台只拿到固定文案：

```
14:43:11.426 ERROR [http-nio-8010-exec-2] [2f20772005424cbe] c.w.s.b.web.GlobalExceptionHandler
  - 未预期异常：uri=/api/demo/system-error
java.lang.NullPointerException: null
	at com.wkedong.springboot.basics.web.DemoController.systemError(DemoController.java:69)
```

## 思考点

1. 「HTTP 200 + 业务码」与「HTTP 4xx/5xx + 业务码」两种风格各有什么代价？网关重试、熔断统计、
   前端拦截器分别偏好哪一种？（本模块选了后者，为什么？）
2. traceId 要跨线程、跨服务、跨 MQ 传递，哪些环节是自动的，哪些必须手工透传？
   （提示：HTTP 头、线程池、Kafka 消息头）
3. 参数校验放在 DTO、Service 还是数据库约束？三层都放算不算重复？
4. 拦截器做鉴权与网关统一鉴权（docs/19）各自的边界在哪？如果服务被内网直连，拦截器还够用吗？
5. 全局异常处理器捕获 `Exception` 会不会掩盖本应暴露的问题？如何避免「兜底变成遮羞布」？
