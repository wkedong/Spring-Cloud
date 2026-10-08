package com.wkedong.springcloud.security.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 无权限处理器（403）。
 * <p>
 * <b>默认行为</b>：Spring Security 回一个无 body 的 403。这里替换成统一 JSON，并且明确告诉用户
 * 「你已经是登录状态，只是权限不够」——这类信息不敏感，说清楚能省掉一轮沟通。
 * <p>
 * <b>关键点：403 语义是「知道你是谁，但你不够格」</b>。触发它的两种写法在本模块都能看到：
 * <ul>
 *   <li>URL 级：{@code .antMatchers("/api/admin/**").hasRole("ADMIN")}；</li>
 *   <li>方法级：{@code @PreAuthorize("hasRole('ADMIN')")}——
 *       它抛出的 {@code AccessDeniedException} 一样会冒泡到过滤器链，被本处理器接住，
 *       所以方法级鉴权同样得到 403 而不是 500。</li>
 * </ul>
 *
 * @author wkedong
 */
@Component
@ConditionalOnProperty(prefix = "security.auth.json-error", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        AuthErrorWriter.write(response, HttpServletResponse.SC_FORBIDDEN,
                "无权限：当前身份缺少访问该资源所需的角色（例如需要 ROLE_ADMIN）");
    }
}
