package com.wkedong.springcloud.security.controller;

import com.wkedong.springcloud.security.gateway.JwtAuthGlobalFilter;
import com.wkedong.springcloud.security.web.ApiResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模拟「网关后面的业务服务」：只负责读网关下传的用户信息，自己不做鉴权。
 * <p>
 * 用途是让「请求头下传」这件看不见的事变得可观测：
 * <ul>
 *   <li>网关过滤器关闭时：这两个头谁都能伪造（客户端直接写 {@code X-User-Id: hacker} 就能被读到），
 *       这正说明「业务服务绝不能裸露在网络里，也不能盲信这类头」；</li>
 *   <li>网关过滤器打开时：过滤器会把客户端传来的同名头**覆盖**成令牌里的真实身份。</li>
 * </ul>
 * 生产里的正确姿势是：业务服务只接受来自网关的内网流量（安全组/服务网格保证），
 * 并且入口处把外部带来的 {@code X-User-*} 头全部剥掉。
 *
 * @author wkedong
 */
@RestController
@RequestMapping("/api/gateway")
public class GatewayEchoController {

    /**
     * 回显收到的下传头，以及服务端自己认证出来的身份。
     *
     * @param userId   网关下传的用户标识（本模块由 JwtAuthGlobalFilter 写入）
     * @param userRoles 网关下传的角色
     * @param authentication 本服务自己认证出来的身份（资源服务器）
     * @return 对照信息
     */
    @GetMapping("/echo")
    public ApiResponse<Map<String, Object>> echo(
            @RequestHeader(value = JwtAuthGlobalFilter.HEADER_USER_ID, required = false) String userId,
            @RequestHeader(value = JwtAuthGlobalFilter.HEADER_USER_ROLES, required = false) String userRoles,
            Authentication authentication) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("headerUserId", userId);
        data.put("headerUserRoles", userRoles);
        data.put("selfAuthenticatedUser", authentication.getName());
        data.put("selfAuthorities", authentication.getAuthorities().toString());
        return ApiResponse.ok(data);
    }
}
