package com.wkedong.springcloud.security.controller;

import com.wkedong.springcloud.security.service.TokenService;
import com.wkedong.springcloud.security.web.ApiResponse;
import com.wkedong.springcloud.security.web.dto.LoginRequest;
import com.wkedong.springcloud.security.web.dto.TokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;

/**
 * 认证接口：整个系统里**唯一**用「用户名 + 密码」换令牌的入口。
 * <p>
 * 为什么单独抽一个 /auth/login 而不是复用表单登录：
 * <ol>
 *   <li>表单登录是「服务端会话」时代的产物：登录成功写 Session + 重定向，不适合前后端分离；</li>
 *   <li>API 化的登录能被网关、App、小程序、其他服务统一调用，返回结构固定（JSON）；</li>
 *   <li>认证与授权由此解耦：认证只做一次（发令牌），授权在每个服务本地完成（验令牌）。</li>
 * </ol>
 * 这个接口是「不能带令牌也能访问」的少数接口之一，所以必须在过滤链里显式放行。
 *
 * @author wkedong
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    /** hasRole('ADMIN') 比对的是 ROLE_ADMIN，写进 JWT 时统一去掉前缀，保持 claim 干净 */
    private static final String ROLE_PREFIX = "ROLE_";

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;

    public AuthController(AuthenticationManager authenticationManager, TokenService tokenService) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
    }

    /**
     * 账号密码登录，成功返回 JWT。
     * <p>
     * 流程：{@code AuthenticationManager.authenticate} 走完整认证链
     * （DaoAuthenticationProvider → UserDetailsService 取用户 → BCrypt 比对密码），
     * 成功后再由 {@link TokenService} 把身份「翻译」成令牌。
     * 认证失败会抛 {@code BadCredentialsException}，由 RestExceptionHandler 统一转成 401 JSON。
     *
     * @param request 用户名 + 密码
     * @return 令牌响应
     */
    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

        List<String> roles = new ArrayList<>();
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String value = authority.getAuthority();
            roles.add(value.startsWith(ROLE_PREFIX) ? value.substring(ROLE_PREFIX.length()) : value);
        }

        TokenResponse token = tokenService.issue(authentication.getName(), roles);
        // 只记用户名，不记密码、不记令牌全文：日志经常被集中收集，令牌进日志等于密钥泄露
        log.info("用户登录成功：user={}, roles={}", authentication.getName(), roles);
        return ApiResponse.ok(token);
    }
}
