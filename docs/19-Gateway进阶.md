# 19 · Gateway 进阶：GlobalFilter 鉴权、入口限流与路由重试
> 模块：`zuul/`（6050）｜前置阅读：docs/06-服务网关.md、docs/05-服务容错保护.md

## 学什么

06 篇讲「Zuul 1.x 怎么迁到 Spring Cloud Gateway、路由怎么配」；本篇讲网关真正承担职责的三件事：**入口鉴权、入口限流、失败重试**——都靠 `GlobalFilter` + `Ordered` 实现。过滤器链是请求进入系统的唯一一层公共切面，链上顺序即语义；模块目录仍叫 `zuul`（配置键已从 `zuul.routes.*` 迁到 `spring.cloud.gateway.server.webflux.routes.*`——**2025.1 起整棵配置树比 2021.0 时代又下移了一层**，包名也从 `...springcloud.zuul` 改成 `...springcloud.gateway`），看日志时别被它迷惑。

| 生产问题 | 本篇机制 | 本模块实现 |
| --- | --- | --- |
| 每个服务都要自己校验 token | `GlobalFilter` + 负值 `Ordered` | `zuul/.../gateway/filter/AuthGlobalFilter.java`（-100） |
| 某个调用方把后端打满 | 入口限流（单机固定窗口） | `zuul/.../gateway/filter/InMemoryRateLimitGlobalFilter.java`（-90） |
| 下游偶发 5xx 就整条链路失败 | 路由级 `Retry` 过滤器 | `zuul/src/main/resources/application.yml` 的 `service-producer-retry` |
| 前端跨域要到处配 | `spring.cloud.gateway.server.webflux.globalcors` | 同上 |
| 下游不知道「调用者是谁」 | 鉴权通过后注入内部头 | 网关下发 `X-User-Id` / `X-User-From` |

## 核心代码

### 1. 鉴权：负序 GlobalFilter，短路时自己写响应

```java
public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String path = exchange.getRequest().getURI().getPath();
    if (!path.startsWith("/api/") || isWhiteListed(path)
            || HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod())) {
        return chain.filter(exchange);          // 非业务路由 / 白名单 / CORS 预检 → 放行
    }
    if (!expectedToken.equals(exchange.getRequest().getHeaders().getFirst("X-Token"))) {
        return reject(exchange, HttpStatus.UNAUTHORIZED, 40100, "未认证：请在请求头携带合法的 X-Token（网关统一鉴权）");
    }
    ServerHttpRequest mutated = exchange.getRequest().mutate()
            .header("X-User-Id", "10086").header("X-User-From", "gateway").build();
    return chain.filter(exchange.mutate().request(mutated).build());
}
```

白名单是 `Arrays.asList("/api/public/", "/api/open/")`，`getOrder()` 返回 **-100**。鉴权失败是自己写响应，WebFlux 下必须凑齐三件套：`response.setStatusCode(status)` → `getHeaders().setContentType(JSON)` → `return response.writeWith(Mono.just(buffer))`；少了最后一行，客户端只会拿到一个空的 200。

### 2. 限流：-90 排在鉴权之后，key = 客户端 IP + 路径前两段

```java
String key = clientIp(exchange) + "|" + prefixOf(path);   // 实测 key：127.0.0.1|api/producer
Window window = windows.computeIfAbsent(key, k -> new Window());
synchronized (window) {
    long now = System.currentTimeMillis();
    if (now - window.startMillis >= windowMillis) { window.startMillis = now; window.count.set(0); }
    used = window.count.incrementAndGet();
    allowed = used <= capacity;                            // capacity=10、windowMillis=10000
}
exchange.getResponse().beforeCommit(() -> {                // ← 提交前写，才不会被上游覆盖
    exchange.getResponse().getHeaders().set("X-RateLimit-Limit", String.valueOf(capacity));
    exchange.getResponse().getHeaders().set("X-RateLimit-Remaining", String.valueOf(Math.max(0, capacity - used)));
    return Mono.empty();
});
```

`getOrder()` 返回 **-90**：鉴权（-100）先跑、限流（-90）后跑。这不是审美问题——鉴权失败时直接 `writeWith` 返回、不调用 `chain.filter()`，后面的限流过滤器**根本不会执行**（实测：连打 6 次无 token 请求全 401，同一个 10 秒窗口里随后仍完整放行 10 次带 token 请求）。

### 3. 路由表、Retry 过滤器与全局 CORS

```yaml
# 2025.1 的写法：starter 更名 spring-cloud-starter-gateway-server-webflux，
# 配置前缀从 spring.cloud.gateway.* 下移到 spring.cloud.gateway.server.webflux.*
# （旧前缀在新版本里不会报错，只是静默不生效——路由、CORS、httpclient 配置全部「消失」）
spring.cloud.gateway.server.webflux:
  discovery.locator.enabled: true            # 自动生成「裸路由」/service-producer/**，注意是小写服务名
  routes:
    - { id: service-producer, uri: "lb://service-producer", predicates: ["Path=/api/producer/**"], filters: ["StripPrefix=2"] }
    - id: service-producer-retry             # 只对 5xx 重试、只重试幂等 GET；retries=2 → 最多 3 次请求
      uri: lb://service-producer
      predicates: [ "Path=/api/retry/**" ]
      filters: [ "StripPrefix=2", { name: Retry, args: { retries: 2, series: SERVER_ERROR, statuses: "BAD_GATEWAY,INTERNAL_SERVER_ERROR" } } ]
  globalcors: { add-to-simple-url-handler-mapping: true, cors-configurations: { "[/**]": { allowedOriginPatterns: "*", allowCredentials: true, maxAge: 3600 } } }
  httpclient: { connect-timeout: 2000, response-timeout: 10s }
gateway: { auth.token: dev-token, ratelimit: { capacity: 10, window-seconds: 10 } }   # 两个自定义过滤器读的配置
# Gateway 5 已删除 management.endpoint.gateway.enabled，改成访问级别开关：
# read-only（可查路由）/ unrestricted（还能改路由）/ none（关掉，/actuator/gateway/routes 直接 404）
management.endpoint.gateway.access: read-only
```

## 关键机制与易错点

1. **网关是 WebFlux 栈，过滤器里绝不能有阻塞调用**：`GlobalFilter` 返回 `Mono<Void>`，跑在 `reactor-http-nio-*` 线程上（实测日志线程名就是 `reactor-http-nio-5`）；写 `RestTemplate`、JDBC、`Thread.sleep` 会卡住整条 event loop，网关线程数很少，一卡就是全站排队。
2. **只在 `chain.filter()` 之前 `add` 响应头，放行路径上的头会被上游覆盖**（本模块踩过的真实坑）：`NettyRoutingFilter` 会用上游的状态行/响应头回写响应，之前 add 的头在放行路径被冲掉，表现为「429 有 `X-RateLimit-*`、正常 200 反而没有」；正确做法是 `response.beforeCommit(() -> {...})`，在响应提交前写入。
3. **`GlobalFilter` 必须落在主应用类所在包及其子包下才会被扫描到**（另一个真实坑）：把过滤器放进 `com.wkedong.springcloud.zuul.filter`、而应用类在 `com.wkedong.springcloud.gateway`，过滤器会**静默不生效**——限流完全不触发、连响应头都没有、日志一行报错都没有；排查动作是 `/actuator/beans` 按类名搜，找不到这个 bean 就是没注册。
4. **`OPTIONS` 预检要放行，判断要放在鉴权之前**：否则浏览器侧跨域永远失败，且前端控制台看不出原因。实测预检返回 `Access-Control-Allow-Origin / Allow-Methods / Allow-Credentials: true / Max-Age: 3600`，它命中 `globalcors` 并在过滤器链之前短路，所以**不占用限流配额**。
5. **「白名单」只等于「不鉴权」，不等于「能转发」**：`/api/public/**` 在路由表里没有任何路由匹配，请求在 `RoutePredicateHandlerMapping` 阶段就 404，`GlobalFilter` 链压根没跑，所以那个 404 不带 `X-RateLimit-*`、不消耗配额、报文是网关自己生成的；对照 `/api/producer/public/ping` 命中路由、过滤器链正常执行，404 来自 producer（`"path":"/public/ping"`，前缀已剥掉）且带 `X-RateLimit-Limit: 10 / X-RateLimit-Remaining: 9`。
6. **自研限流是「单机固定窗口」，缺陷要讲清楚**：窗口边界效应（00:09 打满、00:11 再打满，瞬时 2 倍流量通过）、多实例各算一份（集群总配额放大 N 倍）、进程重启计数清零；生产应交回 `RequestRateLimiter` + Redis，用 `KeyResolver` 决定限流维度。
7. **Retry 只争取「多一次机会」，不能把 5xx 变成 200**：`/api/retry/**` 配了 `retries: 2`，实测一次网关请求让 producer 的 `/testError` 被调用 3 次（12 → 15，差值 3 = 重试 2 次 + 原始 1 次），网关最终**仍返回 HTTP 500**；重试会成倍放大下游压力，所以只对幂等 GET 开，并配好退避（`firstBackoff`/`maxBackoff`）。
8. **内部头是「可信来源」，前提是网络隔离**：业务服务信任 `X-User-Id` 的前提是外部流量只能走网关。实测经网关访问 `/api/producer/echoHeaders`，producer 收到 `x-user-id: 10086`、`x-user-from: gateway`，同一报文里还能看到 `x-token: dev-token` 被原样转发下去，生产上更稳妥的是覆盖而非追加，并剥掉可伪造的头。

## 动手验证

```bash
# ① 路由表：9 条 = 6 条 discovery locator 自动生成 + 3 条自定义
curl -s http://127.0.0.1:6050/actuator/gateway/routes | \
  python3 -c "import sys,json;d=json.load(sys.stdin);print('路由数:',len(d));[print(' -',r['route_id'],r['uri']) for r in d]"
# 路由数: 9
#  - ReactiveCompositeDiscoveryClient_CONFIG / _SERVICE-CONSUMER-RIBBON-HYSTRIX / _SERVICE-CONSUMER-RIBBON / _ZUUL / _SERVICE-CONSUMER-FEIGN / _SERVICE-PRODUCER   （6 条，lb://大写服务ID）
#  - service-producer lb://service-producer  - service-consumer lb://service-consumer  - service-producer-retry lb://service-producer
# 期望看到什么：ReactiveCompositeDiscoveryClient_ 前缀的 6 条就是 discovery.locator.enabled=true 的效果，路径形如 /service-producer/**（服务 id 大写、裸路由、无业务前缀）；后 3 条是手写的，走 /api/** 前缀。

# ② 鉴权两态：无 token 401 / 带 token 200
curl -s -i http://127.0.0.1:6050/api/producer/testGet | head -6
# HTTP/1.1 401 Unauthorized      Content-Type: application/json
# {"code":40100,"message":"未认证：请在请求头携带合法的 X-Token（网关统一鉴权）","path":"/api/producer/testGet","source":"gateway-auth-filter"}
curl -s -H "X-Token: dev-token" http://127.0.0.1:6050/api/producer/testGet
# Hello, Spring Cloud! My port is 6070 Get info is testGet Success
# 期望看到什么：401 + code=40100 + source=gateway-auth-filter，请求没打到业务服务；带 token 后才到达 producer（StripPrefix=2 已剥掉 /api/producer，端口 6070/6080 随负载均衡变化）。

# ③ 白名单 404 与「命中路由的 404」对照 —— 白名单只是不鉴权，不等于能转发
curl -s -i http://127.0.0.1:6050/api/public/ping | head -6
# HTTP/1.1 404 Not Found
# {"timestamp":"2026-10-08T07:37:06.599+00:00","path":"/api/public/ping","status":404,"error":"Not Found","message":null,"requestId":"0602e67b-116"}
curl -s -i -H 'X-Token: dev-token' http://127.0.0.1:6050/api/producer/public/ping | head -8
# HTTP/1.1 404 Not Found      X-RateLimit-Limit: 10      X-RateLimit-Remaining: 9
# {"timestamp":"2026-10-08T07:38:00.293+00:00","status":404,"error":"Not Found","path":"/public/ping"}
# 期望看到什么：第一条无限流头、带 requestId、path 未剥前缀 → 网关自己 404（无路由，过滤器链没跑，也不占配额）；第二条有限流头、path 已剥成 /public/ping → 命中路由并转发，404 来自 producer。

# ④ CORS 预检放行：只靠 globalcors
curl -s -i -X OPTIONS -H 'Origin: http://localhost:8080' -H 'Access-Control-Request-Method: GET' \
  http://127.0.0.1:6050/api/producer/testGet | head -9
# HTTP/1.1 200 OK   Access-Control-Allow-Origin: http://localhost:8080   Access-Control-Allow-Methods: GET
# Access-Control-Allow-Credentials: true   Access-Control-Max-Age: 3600
# 期望看到什么：200 + 这四个允许头，且这次预检不占限流配额；删掉 HttpMethod.OPTIONS 分支，浏览器侧跨域直接失败。

# ⑤ 限流边界：先等一个完整窗口，再连打 12 次（配额 10 次/10 秒）
sleep 12
for i in $(seq 1 12); do printf '第%2d次 HTTP %s\n' "$i" \
  "$(curl -s -o /dev/null -w '%{http_code}' -H 'X-Token: dev-token' http://127.0.0.1:6050/api/producer/testGet)"; done
# 第 1~10 次: HTTP 200          第 11 次起: HTTP 429
# 期望看到什么：11 是分界点；smoke 记录里曾出现「第 10 次就 429」，那是因为循环开始前窗口已被其他复核请求占用过名额——固定窗口按时间算，不按你的循环次数算。过滤器日志写明次数：网关限流命中：key=127.0.0.1|api/producer, 第 11 次请求超过配额 10（窗口 10000 ms）

# ⑥ 429 的响应头与响应体，以及放行路径上的限流头（beforeCommit 修复后的对照）
curl -s -i -H 'X-Token: dev-token' http://127.0.0.1:6050/api/producer/testGet | head -12
# HTTP/1.1 429 Too Many Requests
# X-RateLimit-Limit: 10     X-RateLimit-Remaining: 0     Retry-After: 9
# {"code":42900,"message":"请求过于频繁，请稍后重试","key":"127.0.0.1|api/producer","limit":10,"used":15,"retryAfterSeconds":9,"source":"gateway-ratelimit-filter"}
curl -s -D - -o /dev/null -H 'X-Token: dev-token' http://127.0.0.1:6050/api/producer/testGet | grep -i ratelimit
# X-RateLimit-Limit: 10      X-RateLimit-Remaining: 9
# 期望看到什么：被拒时三个头 + 业务码 42900 + key 带 IP 与路径前缀，used=15 说明计数器统计所有进入过滤器的请求（被拒的也累加）；等窗口过去后正常 200 的响应里同样带 X-RateLimit-*，只有 429 才有就说明用的是 add 而不是 beforeCommit。

# ⑦ 路由级 Retry：一次网关请求 → 下游被调用 3 次
bash /tmp/retry-check.sh
# gateway 返回 HTTP 500 （producer /testError 恒为 500，网关对 5xx 重试 2 次后仍返回 500）；producer 侧 /testError 调用次数: 12 -> 15 （差值 3 = 重试 2 次 + 原始 1 次）
# 期望看到什么：差值恰好 3，而 HTTP 状态码依旧是 500 —— 重试提高的是成功概率，不是错误码。

# ⑧ 内部头注入与过滤器注册情况（排查「静默不生效」）
curl -s -H "X-Token: dev-token" http://127.0.0.1:6050/api/producer/echoHeaders | python3 -m json.tool | head -10
# { "port": 6070, ..., "headers": { "x-token": "dev-token", "x-user-id": "10086", "x-user-from": "gateway", "x-forwarded-prefix": "/api/producer", ... } }
curl -s http://127.0.0.1:6050/actuator/beans | python3 -c \
 "import sys,json;s=json.dumps(json.load(sys.stdin));print('authGlobalFilter','存在' if 'authGlobalFilter' in s else '不存在')"
# authGlobalFilter 存在
# 期望看到什么：x-user-id / x-user-from 由网关注入，业务服务可直接信任，而 x-token 被原样转发（可加固点）；两个自定义过滤器都能在 /actuator/beans 里找到，找不到 = 包不在主应用类扫描范围内。
```

## 思考点

1. 为什么鉴权用 -100、限流用 -90？如果把限流提到 -110（比鉴权更靠前），未认证流量会先被计数——这对防护是好事还是坏事？两种顺序各自的代价是什么？
2. 「在 `chain.filter()` 之前 add 响应头会被上游覆盖」——结合 `NettyRoutingFilter` 的回写时机，说说 `beforeCommit` 为什么能解决，以及它还有哪些常见用法。
3. `/api/public/**` 返回 404 且完全没进过滤器链。据此推断 `RoutePredicateHandlerMapping`、`FilteringWebHandler`、`GlobalFilter` 三者的执行先后？这对「网关兜底统一 404 报文」有什么影响？
4. 网关统一鉴权与业务服务自己的拦截器鉴权（docs/10 的 `AuthInterceptor`）边界在哪？某个服务被内网直连（绕过网关）时，`X-User-Id` 还能信吗？应该怎么防？
5. 单机固定窗口的三个缺陷里，哪一个在生产上最先出事？换成 Redis + 令牌桶后，`KeyResolver` 的维度又该怎么选（提示：`X-Forwarded-For` 可以被伪造）？
6. Retry 重试 2 次后仍返回 500。如果把它和 Sentinel / Resilience4j 的熔断叠在一起，失败率统计该算几次？会不会因为重试反而更快把熔断器打开？
