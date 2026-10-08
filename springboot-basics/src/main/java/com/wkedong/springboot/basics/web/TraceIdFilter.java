package com.wkedong.springboot.basics.web;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * 请求追踪过滤器：为每个请求生成/透传 traceId，写入 MDC 与响应头。
 * <p>
 * 教学点：
 * <ul>
 *   <li>用 {@link OncePerRequestFilter} 而不是裸 Filter：避免 forward/include 时重复执行；</li>
 *   <li>MDC 是<b>线程绑定</b>的（ThreadLocal）：日志模板里写 {@code %X{traceId}} 就能自动带上；
 *       异步线程不会自动继承 MDC，需要显式传递（见 docs 讨论）；</li>
 *   <li>必须在 finally 里 {@code MDC.clear()}：Web 容器复用线程，不清会串号。</li>
 * </ul>
 *
 * @author wkedong
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_KEY = "traceId";
    public static final String TRACE_ID_HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.trim().isEmpty()) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        MDC.put(TRACE_ID_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_KEY);
        }
    }
}
