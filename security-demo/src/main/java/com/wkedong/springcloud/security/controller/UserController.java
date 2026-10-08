package com.wkedong.springcloud.security.controller;

import com.wkedong.springcloud.security.config.JwtProperties;
import com.wkedong.springcloud.security.web.ApiResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 普通用户接口：**只要求「已认证」**，不区分角色。
 * <p>
 * 教学价值：它是「拿到令牌之后能用」的最小闭环，同时把令牌里的内容（sub/roles/exp）
 * 原样回显出来，方便你确认「服务端认到的身份」和「你签发时写进去的内容」是否一致。
 *
 * @author wkedong
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final JwtProperties jwtProperties;

    public UserController(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    /**
     * 查看当前登录者信息。
     * <p>
     * {@code @AuthenticationPrincipal Jwt} 是资源服务器认证成功后放进 SecurityContext 的原始令牌对象；
     * {@code Authentication} 则是 Spring Security 的认证结果（含已映射好的权限）。
     * 两者一起返回，刚好能对照「claim → 权限」的映射结果。
     *
     * @param jwt           令牌本体
     * @param authentication 认证结果
     * @return 当前用户信息
     */
    @GetMapping("/profile")
    public ApiResponse<Map<String, Object>> profile(@AuthenticationPrincipal Jwt jwt,
                                                    Authentication authentication) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sub", jwt.getSubject());
        // 注意坑：jwt.getIssuer() 的返回类型是 URL，issuer 不是 URL 形式时会抛
        // IllegalArgumentException（而不是返回 null），所以这里按字符串取。
        data.put("issuer", jwt.getClaimAsString("iss"));
        data.put("issuedAt", String.valueOf(jwt.getIssuedAt()));
        data.put("expiresAt", String.valueOf(jwt.getExpiresAt()));
        data.put("rolesClaim", jwt.getClaimAsStringList(jwtProperties.getAuthoritiesClaim()));

        List<String> authorities = new ArrayList<>();
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            authorities.add(authority.getAuthority());
        }
        data.put("authorities", authorities);
        return ApiResponse.ok(data);
    }
}
