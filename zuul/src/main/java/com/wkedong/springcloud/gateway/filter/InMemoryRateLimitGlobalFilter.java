package com.wkedong.springcloud.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 网关限流过滤器（内存令牌桶/固定窗口版）。
 * <p>
 * 放在网关做限流的原因：入口限流能保护后面所有服务，且不必每个服务都实现一遍。
 * <p>
 * <b>本实现是「单机固定窗口」</b>，只适合教学与单实例场景，缺陷要讲清楚：
 * <ul>
 *   <li>窗口边界效应：00:09 打满 N 次、00:11 再打满 N 次，瞬时会有 2N 次通过；</li>
 *   <li>多实例部署时每个实例各算一份，集群总配额会被放大成 N 倍；</li>
 *   <li>进程重启计数清零。</li>
 * </ul>
 * 生产应使用 {@code RequestRateLimiter} + Redis（令牌桶算法在 Redis 侧原子执行），
 * 用 {@code KeyResolver} 决定限流维度（用户/接口/IP）。见下方 yml 注释与本目录 docs/19。
 *
 * @author wkedong
 */
@Component
public class InMemoryRateLimitGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(InMemoryRateLimitGlobalFilter.class);
    private static final String GUARDED_PREFIX = "/api/";
    /** 计数维度：客户端 IP + 路径前两段（做到「按接口」限流） */
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<String, Window>();

    private final int capacity;
    private final long windowMillis;

    public InMemoryRateLimitGlobalFilter(@Value("${gateway.ratelimit.capacity:10}") int capacity,
                                        @Value("${gateway.ratelimit.window-seconds:10}") long windowSeconds) {
        this.capacity = capacity;
        this.windowMillis = windowSeconds * 1000L;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (!path.startsWith(GUARDED_PREFIX)) {
            return chain.filter(exchange);
        }
        String key = clientIp(exchange) + "|" + prefixOf(path);
        Window window = windows.computeIfAbsent(key, k -> new Window());
        int used;
        boolean allowed;
        synchronized (window) {
            long now = System.currentTimeMillis();
            if (now - window.startMillis >= windowMillis) {
                window.startMillis = now;
                window.count.set(0);
            }
            used = window.count.incrementAndGet();
            allowed = used <= capacity;
        }
        exchange.getResponse().getHeaders().add("X-RateLimit-Limit", String.valueOf(capacity));
        exchange.getResponse().getHeaders().add("X-RateLimit-Remaining", String.valueOf(Math.max(0, capacity - used)));
        if (!allowed) {
            long retryAfter = Math.max(1, (windowMillis - (System.currentTimeMillis() - window.startMillis)) / 1000);
            logger.warn("网关限流命中：key={}, 第 {} 次请求超过配额 {}（窗口 {} ms）", key, used, capacity, windowMillis);
            exchange.getResponse().getHeaders().add("Retry-After", String.valueOf(retryAfter));
            return reject(exchange, key, used, retryAfter);
        }
        // ⚠️ 实测坑：只在 chain.filter() 之前 add 响应头，**放行路径**上的头会被上游响应头覆盖掉
        // （NettyRoutingFilter 会用上游的状态行/响应头回写）。正确做法是挂 beforeCommit 回调，
        // 在响应真正提交前写入 —— 这样放行与拒绝两条路径都能看到限流信息。
        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().set("X-RateLimit-Limit", String.valueOf(capacity));
            exchange.getResponse().getHeaders().set("X-RateLimit-Remaining", String.valueOf(Math.max(0, capacity - used)));
            return Mono.empty();
        });
        return chain.filter(exchange);
    }

    private Mono<Void> reject(ServerWebExchange exchange, String key, int used, long retryAfter) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":42900,\"message\":\"请求过于频繁，请稍后重试\",\"key\":\"" + key
                + "\",\"limit\":" + capacity + ",\"used\":" + used + ",\"retryAfterSeconds\":" + retryAfter
                + ",\"source\":\"gateway-ratelimit-filter\"}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private String prefixOf(String path) {
        String[] parts = path.split("/");
        // /api/producer/testGet → api/producer（按服务维度限流）
        if (parts.length >= 3) {
            return parts[1] + "/" + parts[2];
        }
        return path;
    }

    private String clientIp(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        return exchange.getRequest().getRemoteAddress() == null
                ? "unknown" : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
    }

    @Override
    public int getOrder() {
        // 在鉴权（-100）之后、路由转发之前执行
        return -90;
    }

    private static class Window {
        private long startMillis = System.currentTimeMillis();
        private final AtomicInteger count = new AtomicInteger();
    }
}
