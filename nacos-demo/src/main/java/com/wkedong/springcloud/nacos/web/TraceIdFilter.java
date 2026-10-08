package com.wkedong.springcloud.nacos.web;

import org.slf4j.MDC;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * 给每个请求打一个 traceId，写进 MDC 与响应头。
 * <p>
 * 教学要点：这个模块要验证「负载均衡两次调用打到了不同实例」，
 * 每台实例的日志里都有同一个 traceId，才能证明「一次调用只落在一台上」。
 * 用 {@code javax.servlet}（Boot 2.7 + Java 8，不能用 jakarta）。
 *
 * @author wkedong
 */
public class TraceIdFilter implements Filter {

    /** MDC 与响应头共用的 key */
    public static final String TRACE_ID_KEY = "traceId";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        MDC.put(TRACE_ID_KEY, traceId);
        if (response instanceof HttpServletResponse) {
            ((HttpServletResponse) response).setHeader(TRACE_ID_HEADER, traceId);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            // 线程池复用线程，必须清理，否则会污染下一个请求
            MDC.remove(TRACE_ID_KEY);
        }
    }
}
