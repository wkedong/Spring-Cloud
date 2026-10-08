package com.wkedong.springcloud.serviceconsumer.feign.interceptor;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.MDC;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.UUID;

/**
 * Feign 请求拦截器：在发起远程调用前统一加工请求头。
 * <p>
 * 教学点：
 * <ol>
 *   <li>典型用途：透传认证信息（token）、业务上下文（灰度标记、租户 id）、自研 traceId；</li>
 *   <li>**不要**在这里手动加 Sleuth 的 X-B3-* 头：Sleuth 有自己的 Feign 集成，会重复注入；</li>
 *   <li>拦截器是「全局/按客户端」配置的横切点，不要在业务方法里零散地 header(...) 拼头。</li>
 * </ol>
 * 验证方式：调用 producer 的 {@code /echoHeaders}，观察 highlight 里是否出现 X-From 与 X-Gray-Version。
 *
 * @author wkedong
 */
public class BusinessHeaderInterceptor implements RequestInterceptor {

    private static final String HEADER_GRAY = "X-Gray-Version";
    private static final String MDC_TRACE_ID = "traceId";

    @Override
    public void apply(RequestTemplate template) {
        // ① 标识调用方，方便下游按来源做统计/限流
        template.header("X-From", "service-consumer-feign");
        // ② 每次调用生成一个业务请求号（与 Sleuth 的 traceId 互补：一个业务号、一个链路号）
        template.header("X-Request-Id", UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        // ③ 透传上游带来的灰度标记（如果调用方是通过 HTTP 进来的）
        HttpServletRequest request = currentRequest();
        if (request != null) {
            String gray = request.getHeader(HEADER_GRAY);
            if (gray != null && !gray.trim().isEmpty()) {
                template.header(HEADER_GRAY, gray);
            }
        }
        // ④ 把当前线程的 MDC traceId 也带上（异步线程不继承 MDC，这里是显式兜底）
        String traceId = MDC.get(MDC_TRACE_ID);
        if (traceId != null) {
            template.header("X-MDC-Trace-Id", traceId);
        }
    }

    static HttpServletRequest currentRequest() {
        // RequestContextHolder 是 ThreadLocal 的：只在处理 HTTP 请求的线程里有效
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes) {
            return ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        }
        return null;
    }
}
