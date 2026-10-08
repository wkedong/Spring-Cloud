# 22 · 认证与授权：Spring Security + JWT

> 模块：security-demo/（8240）。无需注册中心，单机可跑。
> 版本对照：本篇用 Spring Security 5.7 起的组件式配置（`WebSecurityConfigurerAdapter` 已废弃）；当前栈是
> **Spring Security 7**（Boot 4.0.8），方法级开关已换成 `@EnableMethodSecurity`（旧 `@EnableGlobalMethodSecurity`
> 被移除，留着它启动直接失败），匹配器 API 也统一为 `requestMatchers`。写法对照见 [08-升级迁移指南](08-升级迁移指南.md)。

## 学什么

**认证解决「你是谁」，授权解决「你能干什么」**，对应两个状态码：401 是「我不知道你是谁」（没带令牌/令牌无效/已过期），403 是「我知道你是谁，但你不够格」；混用会让前端无法判断该跳登录页还是该提示无权限。

传统做法是登录后写 Session，服务端记住「这个浏览器是谁」。微服务里这条路立刻变贵：请求经负载均衡可能落到任意实例，要么做会话粘滞
（实例挂了会话就丢），要么把 Session 外置到 Redis（多一层基础设施与故障点）。**无状态 Token 换了个思路：把身份写进令牌交给客户端保管，服务端只验签名、不存会话**——实例可以随便扩缩容重启，网关和每个业务服务都能独立验签，不必回调认证中心。

JWT 就是这种令牌的格式，三段 Base64URL 用 `.` 连接：

| 段 | 内容 | 本模块实测取值 |
| --- | --- | --- |
| Header | 算法与密钥 id | `{"kid":"security-demo-hmac-key","typ":"JWT","alg":"HS256"}` |
| Payload | claim 集合 | `{"sub":"admin","roles":["ADMIN","USER"],"iss":"security-demo","exp":...,"iat":...,"jti":"..."}` |
| Signature | 前两段的 HMAC-SHA256 | 用 `security.jwt.secret` 签名，改一个字符就验不过 |

Payload 只是 **Base64 编码，不是加密**：谁拿到令牌都能解出 `sub`/`roles`，安全边界全在签名上（下面有实测）。

## 核心代码

**① 过滤链：Security 5.7 起用 Bean 代替继承**（`config/SecurityConfig.java`）。废弃 `WebSecurityConfigurerAdapter` 是因为「继承 + 覆写」太僵化：一个应用只能有一条由它承担的链；组件式配置把过滤链变成普通 Bean，天然支持多链、按条件组装。

```java
SecurityFilterChain securityFilterChain(HttpSecurity http, /* ... */) throws Exception {
    http.csrf(csrf -> csrf.disable())                  // 纯 Token 场景可关（理由见「关键机制」）
        .formLogin(form -> form.disable()).httpBasic(basic -> basic.disable())  // 关浏览器登录框与 Basic
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))  // 不建 Session
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/auth/**", "/actuator/health").permitAll()   // 白名单要尽量小
            .requestMatchers(HttpMethod.GET, "/api/public/**").permitAll()
            .requestMatchers("/api/admin/**").hasRole("ADMIN")             // URL 级第一道闸
            .anyRequest().authenticated())
        .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint)   // 401 → JSON
                                  .accessDeniedHandler(deniedHandler))     // 403 → JSON
        .oauth2ResourceServer(o -> { o.authenticationEntryPoint(entryPoint);   // 资源服务器错误出口要单独设
            o.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)); });
    return http.build();
}
```

**为什么必须 `STATELESS`**：同一个用户的请求会被打到不同实例，依赖 Session 就要引入会话粘滞或 Redis 共享；JWT 自带身份，服务端没有要保存的东西。**为什么纯 Token 场景可以关 CSRF**：CSRF 成立的前提是「浏览器会自动带上凭证」（Cookie/Session），而凭证放在 `Authorization` 头里、
由前端代码显式添加，第三方站点读不到也伪造不了——**但只要还在用 Cookie/Session 认证，CSRF 就绝不能关**。

**② 签发：`NimbusJwtEncoder` + `OctetSequenceKey`**（`config/JwtConfig.java`、`service/TokenService.java`）。依赖只用 `spring-boot-starter-oauth2-resource-server`（自带 `spring-security-oauth2-jose`），不引第三方 JWT 库：

```java
OctetSequenceKey jwk = new OctetSequenceKey.Builder(properties.secretBytes())
        .algorithm(JWSAlgorithm.HS256).keyID(properties.getKeyId()).build();   // JWK 带 kid，便于密钥轮换
JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
JwtClaimsSet claims = JwtClaimsSet.builder()
        .issuer("security-demo").subject(username)        // iss 防跨系统混用；sub 是唯一用户标识
        .issuedAt(now).expiresAt(now.plusSeconds(ttl)).id(UUID.randomUUID().toString())  // 有效期 + jti
        .claim("roles", roles).build();                   // roles 是自定义 claim，不是标准 scope
```

**③ 解码与权限映射：官方 API 里最容易踩的坑**（下面的 `gac` 再装进 `JwtAuthenticationConverter` 交给资源服务器）：

```java
NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(properties.secretKey())
        .macAlgorithm(MacAlgorithm.HS256).build();                               // 明确只允许 HS256
decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("security-demo")); // exp/nbf + iss
JwtGrantedAuthoritiesConverter gac = new JwtGrantedAuthoritiesConverter();
gac.setAuthoritiesClaimName("roles");   // 默认只认 scope/scp
gac.setAuthorityPrefix("ROLE_");        // 默认前缀是 SCOPE_
```

**④ 方法级鉴权**：启动类上开 `@EnableMethodSecurity`（Spring Security 7 里旧注解 `@EnableGlobalMethodSecurity` 已被移除，
留着它启动直接失败：`IllegalStateException: @EnableGlobalMethodSecurity requires the spring-security-access dependency ...
migrate to @EnableMethodSecurity`；新注解默认 `prePostEnabled=true`，不用再写参数），读写接口都标
`@PreAuthorize("hasRole('ADMIN')")`；`hasRole('ADMIN')` 会自己补 `ROLE_` 前缀再比对，所以 claim 里存干净的 `ADMIN`、映射时补前缀最省心。

## 关键机制

**密码编码**：`BCryptPasswordEncoder`；认证走 `AuthenticationManager` 而不是自己 `matches`——后者会绕过账号锁定/过期、认证事件与 Provider 链。BCrypt 自带随机盐（同一密码两次编码结果不同，彩虹表无效）且故意慢（抬高爆破成本），库里**永远只存哈希**；登录失败一律回「用户名或密码错误」，不区分「用户不存在」与「密码错」，否则接口成了账号枚举器。

**claim 映射坑（已实测）**：默认 `JwtAuthenticationConverter` 只读 `scope`/`scp` 并加 `SCOPE_` 前缀，而本模块令牌里放的是 `roles`，于是「认证通过、授权全丢」。用 `--security.jwt.use-default-converter=true` 启动即可复现：

```bash
curl -s -H "Authorization: Bearer $ADMIN" localhost:8240/api/user/profile
# {"data":{"rolesClaim":["ADMIN","USER"],"authorities":[]}}    ← 令牌里有角色，权限却是空的
curl -s -i -H "Authorization: Bearer $ADMIN" localhost:8240/api/admin/users | head -2
# HTTP/1.1 403                                                  ← 提示还是 insufficient_scope，原因其实在 claim 名
```

**401 vs 403 的语义与默认输出**：Spring Security 默认只回**无 body** 的 401/403（实测 `Content-Length: 0`），前端拿不到可展示的文案，也和本仓库其他模块的统一响应风格不一致；本模块用 `AuthenticationEntryPoint`（401）与 `AccessDeniedHandler`（403）输出同样的 `ApiResponse`，保留 `WWW-Authenticate` 头并补 `error="invalid_token"`。两个衍生坑：
① 资源服务器的错误出口要**单独设置**（`oauth2ResourceServer().authenticationEntryPoint(...)`），只配 `exceptionHandling()` 那处，令牌校验失败依旧是空 body；
② `@PreAuthorize` 抛的 `AccessDeniedException` 若被全局 `@ExceptionHandler(Exception.class)` 兜住，403 会变成 500——本模块在兜底 handler 里把它原样抛出（`/api/demo/method-level` 是这条链路的实测入口）。

**网关鉴权 vs 服务鉴权**：`gateway/JwtAuthGlobalFilter` 是可复用样例（Servlet 版，注释里给了 Spring Cloud Gateway `GlobalFilter` 的改法，拷到 `zuul/` 模块即可落地；本模块未改动网关模块）。

| 方式 | 做法 | 好处 | 代价 |
| --- | --- | --- | --- |
| 网关统一鉴权 | 网关验签，`X-User-Id`/`X-User-Roles` 下传 | 业务服务零改造，无效流量挡在门口 | 业务服务必须只能被网关访问，否则伪造请求头即越权 |
| 各服务资源服务器 | 每个服务自己验签（本模块写法） | 服务可独立部署/测试，纵深防御 | 每个服务都要有密钥；重复验签（一次 HMAC，成本极低） |

生产常见组合是两者叠加：网关做粗粒度拦截（挡住无令牌/过期令牌），服务做细粒度授权（角色、数据权限）。**下传用户信息的关键是「覆盖」而不是「追加」**：实测过滤器关闭时客户端带 `X-User-Id: hacker` 会被原样读到（`"headerUserId":"hacker"`），打开过滤器后同一请求变成 `"headerUserId":"user"`——用户头必须在可信边界上重建。

**令牌过期与刷新**：`exp` 由 `NimbusJwtDecoder` 强制校验（过期令牌实测 401，`error_description="Jwt expired at ..."`）。有效期越长越方便也越危险；生产通常「access token 短（5~30 分钟）+ refresh token 长（天级）」，后者只用于换新令牌，因此能单独存库、单独吊销、单独限流。

## 安全实践

- **密钥管理**：`security.jwt.secret` 由环境变量 `SECURITY_JWT_SECRET` 注入，代码里不留真实密钥（yml 里的默认值只为单机可跑）；不足 32 字节直接启动失败（HS256 要求 ≥256 位）。上线放配置中心加密项，并保证密钥**进不了 Git 与日志**。
- **密钥轮换**：JWK 与 JWT 头部都带 `kid`；轮换时先让所有服务同时信任新旧两把密钥，再用新密钥签发，等旧令牌全部过期后下线旧密钥。
- **HTTPS 是前提**：Payload 只是 Base64，明文 HTTP 上等于广播身份；Bearer 令牌一旦被截获，过期前可直接冒用。
- **`sub` 关联业务用户表**：用不可变主键（用户 id/uuid），别用手机号/邮箱这类会变的字段；服务端拿 `sub` 查本地用户表补齐昵称、部门、数据权限——**令牌里只放鉴权必需的信息**。
- **与 Sentinel/Nacos 的边界**：Nacos 管配置与注册，Sentinel 管流量（限流/熔断），Spring Security 管身份与权限；**Sentinel 拦不住越权，Security 也扛不住流量洪峰**，别指望一个组件解决另一个的问题。

## 动手验证

```bash
# 1) 打包启动（单机，无外部依赖）；内置账号 admin/admin123（ADMIN+USER）、user/user123（只有 USER）
mvn -B -pl security-demo -am package -DskipTests
nohup java -jar security-demo/target/security-demo-0.0.1-SNAPSHOT.jar > /tmp/security-demo-8240.log 2>&1 &
ADMIN_JSON='{"username":"admin","password":"admin123"}'; USER_JSON='{"username":"user","password":"user123"}'
tok() { curl -s -X POST localhost:8240/auth/login -H 'Content-Type: application/json' -d "$1" -o /tmp/t.json
        python3 -c 'import json;print(json.load(open("/tmp/t.json"))["data"]["accessToken"])'; }
ADMIN=$(tok "$ADMIN_JSON"); USER=$(tok "$USER_JSON")
# 2) 登录响应里的令牌片段；解开中段就能看清里面装了什么（Base64 不是加密）
curl -s -X POST localhost:8240/auth/login -H 'Content-Type: application/json' -d "$ADMIN_JSON"
# {"code":0,...,"data":{"accessToken":"eyJraWQiOiJzZWN1cml0eS1kZW1vLWhtYWMta2V5...1qxA_EFxqNVKcogncsgEuR-RK7YqJD-zimMx00KQkQs","tokenType":"Bearer","expiresIn":3600}}
echo "$ADMIN" | cut -d. -f2 | python3 -c 'import sys,base64,json;s=sys.stdin.read().strip();s+="="*(-len(s)%4);print(json.loads(base64.urlsafe_b64decode(s)))'
# {'sub': 'admin', 'roles': ['ADMIN', 'USER'], 'iss': 'security-demo', 'exp': 1791446933, 'iat': 1791443333, 'jti': 'bfe503b3-...'}
# 3) 免认证接口 200；受保护接口不带令牌 → 401（注意 WWW-Authenticate 头与 JSON body）
curl -s -o /dev/null -w 'public/info=%{http_code}\n' localhost:8240/api/public/info      # public/info=200
curl -s -o /dev/null -D - localhost:8240/api/user/profile | head -2 | tr -d '\r'
# HTTP/1.1 401 + WWW-Authenticate: Bearer realm="security-demo"
curl -s localhost:8240/api/user/profile
# {"code":401,"message":"未认证：请先调用 POST /auth/login 获取令牌，并在请求头携带 Authorization: Bearer <accessToken>",...}
# 4) 带令牌 → 200，body 里能看到 claim 与映射后的权限（roles → ROLE_*）
curl -s -H "Authorization: Bearer $ADMIN" localhost:8240/api/user/profile
# {"data":{"sub":"admin","issuer":"security-demo","rolesClaim":["ADMIN","USER"],"authorities":["ROLE_ADMIN","ROLE_USER"],...}}
# 5) user 令牌访问管理员接口 → 403；admin 令牌 → 200
curl -s -i -H "Authorization: Bearer $USER" localhost:8240/api/admin/users | head -1      # HTTP/1.1 403
curl -s -H "Authorization: Bearer $ADMIN" localhost:8240/api/admin/users | head -c 89
# {"code":0,"message":"success","data":[{"username":"admin","roles":"ADMIN,USER","status":"
# 6) 篡改令牌最后一个字符（相当于改 Payload 提权）→ 401；方法级鉴权同样是 403 而不是 500
curl -s -i -H "Authorization: Bearer ${USER%?}X" localhost:8240/api/user/profile | head -2 | tr -d '\r'
# HTTP/1.1 401 + WWW-Authenticate: Bearer realm="security-demo", error="invalid_token"
curl -s -o /dev/null -w 'method-level user=%{http_code} ' -H "Authorization: Bearer $USER" localhost:8240/api/demo/method-level
curl -s -o /dev/null -w 'admin=%{http_code}\n' -H "Authorization: Bearer $ADMIN" localhost:8240/api/demo/method-level
# method-level user=403 admin=200
```

升级后实测的安全矩阵（四条出口一次看全）：**无 token → 401；user 令牌访问 ADMIN 接口 → 403；
方法级 `@PreAuthorize` 对 user → 403；公开端点 → 200**。两个 403 的来源不同（URL 级 `hasRole('ADMIN')` 与方法级注解），
但对客户端是同一个状态码，排错时要分清是哪一道闸拦的。

令牌失效的几种场景原因不同、但对客户端都是 401，原因只在响应头里（默认输出模式才带 `error_description`；本模块的 JSON 输出刻意只回统一文案）：

| 场景 | 默认实现给出的 `WWW-Authenticate` |
| --- | --- |
| 乱写令牌（不是 JWT） | `error="invalid_token", error_description="... Invalid JWT serialization: Missing dot delimiter(s)"` |
| 令牌过期 | `error="invalid_token", error_description="... Jwt expired at 2026-10-08T05:57:32Z"` |
| `iss` 不匹配（别家系统签发） | `error="invalid_token", error_description="... The iss claim is not valid"` |
| 篡改 Payload / 换密钥签发 | `error="invalid_token", error_description="... Signed JWT rejected: Invalid signature"` |

网关侧鉴权同样实测过（加 `--security.gateway.enabled=true` 启动）：不带令牌访问 `/api/user/profile` 得到 `401 {"message":"网关鉴权失败：请求未携带 Authorization ..."}`；带合法令牌 + 伪造 `X-User-Id: hacker` 请求 `/api/gateway/echo`，回显 `{"headerUserId":"user","headerUserRoles":"USER",...}`——伪造头被令牌里的真实身份覆盖。

## 思考点

- **JWT 无法主动失效怎么办**：验签是纯本地的，服务端没有「已签发令牌」清单，改密码/封号后旧令牌在 `exp` 前依然有效。折中方案：短有效期 + refresh token；令牌里带 `tokenVersion`、用户表存当前版本，升级即全端失效（每请求多查一次缓存）；只对高危操作走 `jti` 黑名单（存 Redis 到过期为止）。没有银弹，只有「失效多快」与「每请求多少成本」的取舍。
- **OAuth2 授权服务器要不要自建**：只有需要第三方应用授权、单点登录、多租户这类真正的 OAuth2 场景才值得；「自家前端调自家后端」，本模块「认证接口发 JWT + 资源服务器验 JWT」已经够用，上授权服务器是纯增复杂度。
- **密钥轮换**：轮换不是「换掉」而是「并存 + 灰度」；还要想清楚 `kid` 怎么下发、旧密钥保留多久（≥ 最长令牌有效期）。
- **令牌里能放什么、不能放什么**：能放——`sub`、角色、`exp`、授权范围（变一次代价可接受）；不能放——手机号/身份证等隐私（Payload 谁都能解）、余额/库存等频繁变化的值（令牌是不可变快照）、任何足以直接越权的密钥或内部地址。
