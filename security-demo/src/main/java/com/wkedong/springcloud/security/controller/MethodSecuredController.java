package com.wkedong.springcloud.security.controller;

import com.wkedong.springcloud.security.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 方法级鉴权独立演示。
 * <p>
 * 这个路径在过滤链里只要求「已认证」（没有 URL 级角色规则），
 * 角色判断**完全**由方法上的 {@code @PreAuthorize} 完成——
 * 用来回答一个很常见的疑问：方法级鉴权抛出的 {@code AccessDeniedException}
 * 会不会变成 500？答案是会正常变成 403，因为异常会冒泡回过滤器链被
 * {@code AccessDeniedHandler} 接住（本模块已实测，见文档「关键机制」）。
 *
 * @author wkedong
 */
@RestController
@RequestMapping("/api/demo")
public class MethodSecuredController {

    /**
     * 只有 ADMIN 能访问；普通用户拿到的是 403 而不是 401（因为他确实是已登录用户）。
     *
     * @param authentication 当前认证信息
     * @return 方法级鉴权通过的回执
     */
    @GetMapping("/method-level")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> methodLevel(Authentication authentication) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message", "方法级鉴权通过：@PreAuthorize(\"hasRole('ADMIN')\")");
        data.put("user", authentication.getName());
        return ApiResponse.ok(data);
    }
}
