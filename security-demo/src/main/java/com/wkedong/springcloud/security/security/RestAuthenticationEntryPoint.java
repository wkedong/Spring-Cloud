package com.wkedong.springcloud.security.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.BearerTokenErrorCodes;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 未认证处理器（401）。
 * <p>
 * <b>默认行为</b>：Spring Security 走 {@code BearerTokenAuthenticationEntryPoint}，只回状态码 401 与
 * {@code WWW-Authenticate} 头，**响应体是空的**。对浏览器/前端不友好，
 * 也和我们其他模块「统一 JSON 响应」的风格不一致，所以这里替换掉。
 * <p>
 * <b>关键点：401 语义是「你是谁我不知道」</b>——没带令牌、令牌签名不对、令牌过期，
 * 都属于这一类，客户端收到 401 的**正确动作是重新登录/刷新令牌**；
 * 而「知道你是谁，但你没有这个权限」必须是 403（见 {@link RestAccessDeniedHandler}）。
 * 把两者混用会让前端无法判断「该跳登录页」还是「该提示无权限」。
 * <p>
 * 本类可通过 {@code security.auth.json-error.enabled=false} 关闭，
 * 用来实测框架默认的「无 body 401」行为（教学对比用）。
 *
 * @author wkedong
 */
@Component
@ConditionalOnProperty(prefix = "security.auth.json-error", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        // WWW-Authenticate 是 HTTP 规范里 401 的必备响应头，告诉客户端「用 Bearer 方式带令牌重试」。
        // 令牌本身有问题时补上 error 参数（对应 RFC 6750），客户端可据此区分「没带」与「带了但无效」。
        String challenge = "Bearer realm=\"security-demo\"";
        if (authException instanceof OAuth2AuthenticationException) {
            challenge += ", error=\"" + BearerTokenErrorCodes.INVALID_TOKEN + "\"";
        }
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, challenge);

        // 提示语刻意**不区分**「未携带令牌 / 令牌过期 / 签名错误」：
        // 对调用方来说处理动作都一样（重新登录），对外说得越少越安全。
        AuthErrorWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED,
                "未认证：请先调用 POST /auth/login 获取令牌，并在请求头携带 Authorization: Bearer <accessToken>");
    }
}
