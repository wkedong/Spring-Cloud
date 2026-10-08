package com.wkedong.springboot.basics.web;

import com.wkedong.springboot.basics.config.BasicsProperties;
import com.wkedong.springboot.basics.exception.BusinessException;
import com.wkedong.springboot.basics.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 鉴权拦截器（教学示例：拦截器比过滤器更适合做「接口级」控制）。
 * <p>
 * 校验请求头 {@code X-Token} 是否等于配置项 {@code basics.security-token}。
 * <ul>
 *   <li>OPTIONS 预检请求直接放行（否则跨域会失败）；</li>
 *   <li>鉴权失败<b>抛异常</b>而不是写响应：交给全局异常处理器统一输出结构，
 *       避免「有的地方返回 JSON、有的地方返回 401 纯文本」；</li>
 *   <li>afterCompletion 记录耗时：慢请求排查的第一手数据。</li>
 * </ul>
 * 生产实践：这里是演示用的静态 token，真实项目应换成 JWT/OAuth2 校验
 * （见 Spring Cloud 部分的网关鉴权章节）。
 *
 * @author wkedong
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);
    private static final String TOKEN_HEADER = "X-Token";
    private static final String START_TIME_ATTR = "basics.requestStart";

    private final BasicsProperties properties;

    public AuthInterceptor(BasicsProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());
        String token = request.getHeader(TOKEN_HEADER);
        if (!properties.getSecurityToken().equals(token)) {
            log.warn("鉴权失败：uri={}, 期望请求头 {}（演示用 token 见配置 basics.security-token）",
                    request.getRequestURI(), TOKEN_HEADER);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未认证：请在请求头携带 " + TOKEN_HEADER);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        Object start = request.getAttribute(START_TIME_ATTR);
        if (start instanceof Long) {
            long cost = System.currentTimeMillis() - (Long) start;
            log.debug("请求完成：{} {} -> {} ({} ms)", request.getMethod(), request.getRequestURI(),
                    response.getStatus(), cost);
        }
    }
}
