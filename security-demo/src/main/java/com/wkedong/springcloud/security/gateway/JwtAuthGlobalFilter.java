package com.wkedong.springcloud.security.gateway;

import com.wkedong.springcloud.security.config.JwtProperties;
import com.wkedong.springcloud.security.security.AuthErrorWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * 网关侧统一鉴权过滤器（可复用样例）。
 * <p>
 * <b>它是什么</b>：一个「在业务服务之前把令牌验掉，并把身份下传」的过滤器。
 * 本仓库的网关是 {@code zuul/} 模块（实现为 Spring Cloud Gateway，WebFlux）；
 * 这个类是 Servlet 版本的教学样例，<b>把它拷到网关模块并改成 GlobalFilter 即可落地</b>，
 * 核心逻辑（取令牌 → 验签 → 失败 401 → 成功下传身份）一字不变。
 *
 * <p><b>对应 Spring Cloud Gateway 的写法（拷贝时改这三处）</b>：
 * <pre>
 * // 1) 过滤器接口：Servlet 的 FilterChain → WebFlux 的 GatewayFilterChain
 * // 2) 方法签名：doFilterInternal(req,resp,chain) → Mono&lt;Void&gt; filter(ServerWebExchange exchange, GatewayFilterChain chain)
 * // 3) 发请求：chain.doFilter(request, response) → chain.filter(exchange.mutate().request(mutatedRequest).build())
 *
 * &#64;Component
 * public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {
 *     private final ReactiveJwtDecoder jwtDecoder;   // NimbusReactiveJwtDecoder.withSecretKey(key).macAlgorithm(HS256).build()
 *
 *     &#64;Override
 *     public Mono&lt;Void&gt; filter(ServerWebExchange exchange, GatewayFilterChain chain) {
 *         String path = exchange.getRequest().getURI().getPath();
 *         if (whitelisted(path)) {                       // 白名单直接放行
 *             return chain.filter(exchange);
 *         }
 *         String token = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
 *         return jwtDecoder.decode(token.substring(7))    // 注意返回 Mono，用 onErrorResume 写 401
 *                 .flatMap(jwt -&gt; chain.filter(exchange.mutate()
 *                         .request(exchange.getRequest().mutate()
 *                                 .header("X-User-Id", jwt.getSubject())
 *                                 .header("X-User-Roles", String.join(",", jwt.getClaimAsStringList("roles")))
 *                                 .build())
 *                         .build()))
 *                 .onErrorResume(JwtException.class, e -&gt; write401(exchange));
 *     }
 * }
 * </pre>
 *
 * <p><b>两种落地方式怎么选</b>（详见 docs/22）：
 * <ol>
 *   <li><b>网关统一鉴权 + 请求头下传</b>：令牌只验一次，业务服务零改造，
 *       但业务服务必须做到「只接受网关来的流量」，否则伪造 {@code X-User-*} 头即可越权；</li>
 *   <li><b>各服务自己做资源服务器</b>（本模块 {@code SecurityConfig} 的写法）：
 *       每个服务独立校验、可独立部署与测试，代价是重复校验（一次 HMAC-SHA256 验签，成本很低），
 *       并且每个服务都要能拿到密钥/公钥。</li>
 * </ol>
 * 生产常见组合：网关做**粗粒度**拦截（挡住无令牌/过期令牌，省掉无效流量），
 * 各服务做**细粒度**授权（角色、数据权限），两者不冲突。
 *
 * <p><b>本类为什么默认关闭</b>：本模块已经用资源服务器做鉴权，
 * 再叠一层会让教学主线变模糊，所以用 {@code security.gateway.enabled} 开关控制，
 * 打开后可实测「网关先验、身份下传」的效果。
 *
 * @author wkedong
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
@ConditionalOnProperty(prefix = "security.gateway", name = "enabled", havingValue = "true")
public class JwtAuthGlobalFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthGlobalFilter.class);

    /** 下传给业务服务的用户标识头 */
    public static final String HEADER_USER_ID = "X-User-Id";

    /** 下传给业务服务的角色头（逗号分隔） */
    public static final String HEADER_USER_ROLES = "X-User-Roles";

    private static final String BEARER_PREFIX = "Bearer ";

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final JwtDecoder jwtDecoder;
    private final JwtProperties jwtProperties;
    private final GatewayAuthProperties gatewayAuthProperties;

    public JwtAuthGlobalFilter(JwtDecoder jwtDecoder, JwtProperties jwtProperties,
                               GatewayAuthProperties gatewayAuthProperties) {
        this.jwtDecoder = jwtDecoder;
        this.jwtProperties = jwtProperties;
        this.gatewayAuthProperties = gatewayAuthProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // 1) 不在保护范围内的路径（非 /api/**）直接放行
        if (!matchesAny(path, gatewayAuthProperties.getProtectedPaths())) {
            filterChain.doFilter(request, response);
            return;
        }
        // 2) 白名单（登录、公开接口、健康检查）放行
        if (matchesAny(path, gatewayAuthProperties.getWhitelist())) {
            filterChain.doFilter(request, response);
            return;
        }

        // 3) 取令牌
        String token = resolveToken(request);
        if (token == null) {
            log.debug("网关鉴权失败：缺少 Authorization 头，path={}", path);
            AuthErrorWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "网关鉴权失败：请求未携带 Authorization: Bearer <accessToken>");
            return;
        }

        // 4) 验签 + 校验 exp/iss —— 一次本地计算，不查库、不调远程服务
        try {
            Jwt jwt = jwtDecoder.decode(token);
            List<String> roles = jwt.getClaimAsStringList(jwtProperties.getAuthoritiesClaim());
            String roleHeader = roles == null ? "" : String.join(",", roles);

            // 5) 把身份下传给业务服务（包装器会覆盖客户端伪造的同名头）
            HttpServletRequest wrapped = new UserHeaderRequestWrapper(request, jwt.getSubject(), roleHeader);
            filterChain.doFilter(wrapped, response);
        } catch (JwtException | IllegalArgumentException ex) {
            // 只记原因，不回给调用方（避免把「签名不对/已过期」这类细节暴露出去）
            log.warn("网关鉴权失败：令牌校验不通过，path={}, reason={}", path, ex.getMessage());
            AuthErrorWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "网关鉴权失败：令牌无效或已过期（由 JwtAuthGlobalFilter 拦截）");
        }
    }

    /** 从 Authorization 头里取 Bearer 令牌；没有或格式不对都返回 null */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private boolean matchesAny(String path, List<String> patterns) {
        if (patterns == null) {
            return false;
        }
        for (String pattern : patterns) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }
}
