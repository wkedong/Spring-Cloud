package com.wkedong.springcloud.security.controller;

import com.wkedong.springcloud.security.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 公开接口：不需要任何令牌。
 * <p>
 * 为什么「放行白名单」要写得很小：每多放行一个路径，就多一块攻击面。
 * 白名单应该只包含三类东西——登录/注册/验证码、健康检查（给监控用）、真正无隐私的公开数据。
 *
 * @author wkedong
 */
@RestController
@RequestMapping("/api/public")
public class PublicController {

    /**
     * 公开信息（免认证）。
     *
     * @return 服务与可用接口说明
     */
    @GetMapping("/info")
    public ApiResponse<Map<String, Object>> info() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("service", "security-demo");
        data.put("port", 8240);
        data.put("serverTime", Instant.now().toString());
        data.put("authenticated", Boolean.FALSE);
        data.put("loginApi", "POST /auth/login {\"username\":\"admin\",\"password\":\"admin123\"}");
        data.put("hint", "受保护接口需带请求头 Authorization: Bearer <accessToken>");
        return ApiResponse.ok(data);
    }
}
