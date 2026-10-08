package com.wkedong.springcloud.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * 网关统一鉴权过滤器（GlobalFilter）。
 * <p>
 * 为什么鉴权要放在网关：
 * <ol>
 *   <li>只在入口处校验一次，业务服务不必各自实现一套 token 校验；</li>
 *   <li>未认证的流量在入口就被拦掉，不会打到后端集群；</li>
 *   <li>校验通过后可以把用户身份（userId）以内部请求头下传，业务服务直接信任内网头。</li>
 * </ol>
 * 实现要点与坑：
 * <ul>
 *   <li>GlobalFilter 是 **反应式** 的：返回值是 {@code Mono<Void>}，绝不能在这里做阻塞调用（如 RestTemplate、JDBC）；</li>
 *   <li>{@link #getOrder()} 要小于 0（本过滤器 -100），保证在路由/转发相关过滤器之前执行；</li>
 *   <li>自己写响应必须显式设置状态码与 Content-Type，并返回 {@code response.writeWith(...)}，
 *       否则客户端会拿到空的 200；</li>
 *   <li>跨域预检请求（OPTIONS）必须放行，且不要给它套鉴权，否则浏览器侧永远失败；</li>
 *   <li>只拦截业务路由 {@code /api/**}：本模块还开着 discovery-locator 生成的「裸路由」
 *       （{@code /service-producer/**}）用于教学对比，生产环境应关闭 locator 或一并纳入白/黑名单。</li>
 * </ul>
 * 生产实践：JWT / OAuth2 场景可直接用 Spring Security 的
 * {@code SecurityWebFilterChain}，或把校验委托给授权服务器；本类演示的是「最小可用鉴权骨架」。
 *
 * @author wkedong
 */
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(AuthGlobalFilter.class);

    /** 需要鉴权的路径前缀：业务路由 */
    private static final String GUARDED_PREFIX = "/api/";
    /** 鉴权白名单（无需 token） */
    private static final List<String> WHITE_LIST = Arrays.asList("/api/public/", "/api/open/");

    private final String expectedToken;

    public AuthGlobalFilter(@Value("${gateway.auth.token:dev-token}") String expectedToken) {
        this.expectedToken = expectedToken;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (!path.startsWith(GUARDED_PREFIX) || isWhiteListed(path)
                || HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod())) {
            // 非业务路由（actuator、discovery-locator 裸路由）与白名单直接放行
            return chain.filter(exchange);
        }
        String token = exchange.getRequest().getHeaders().getFirst("X-Token");
        if (!expectedToken.equals(token)) {
            logger.warn("网关鉴权失败：path={}, 携带 token={}, 期望 token={}", path, token, expectedToken);
            return reject(exchange, HttpStatus.UNAUTHORIZED, 40100,
                    "未认证：请在请求头携带合法的 X-Token（网关统一鉴权）");
        }
        // 鉴权通过：把用户身份编码后下传给业务服务（生产用 JWT 里的 subject / userId）
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header("X-User-Id", "10086")
                .header("X-User-From", "gateway")
                .build();
        logger.info("网关鉴权通过：path={}, 已注入内部头 X-User-Id", path);
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private boolean isWhiteListed(String path) {
        for (String prefix : WHITE_LIST) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** 写回统一格式的 JSON 错误响应（与业务服务的 ApiResponse 结构保持一致） */
    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, int code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":" + code + ",\"message\":\"" + message + "\",\"path\":\""
                + exchange.getRequest().getURI().getPath() + "\",\"source\":\"gateway-auth-filter\"}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // 越小越先执行；鉴权要在路由转发之前
        return -100;
    }
}
