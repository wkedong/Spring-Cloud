package com.wkedong.springcloud.security.service;

import com.wkedong.springcloud.security.config.JwtProperties;
import com.wkedong.springcloud.security.web.dto.TokenResponse;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 令牌签发服务：把「认证成功的身份」翻译成一张自包含的通行证（JWT）。
 * <p>
 * 为什么 JWT 适合微服务：
 * <ol>
 *   <li><b>无状态</b>：令牌自带身份与角色，服务端不用存 Session，
 *       服务实例可以随便扩缩容、重启，也不会因为「会话在别的实例上」而失效；</li>
 *   <li><b>可自校验</b>：资源服务用密钥验签即可判断真假，不必每次都回调认证中心（省一跳 RPC）；</li>
 *   <li><b>跨语言/跨服务通用</b>：任何语言的网关、服务都能验签，不绑定 Java 会话实现。</li>
 * </ol>
 * 代价也要记住：**签发出去的令牌在过期前无法真正撤回**（除非再引入黑名单/版本号），详见文档思考点。
 *
 * @author wkedong
 */
@Service
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;

    public TokenService(JwtEncoder jwtEncoder, JwtProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    /**
     * 签发访问令牌。
     *
     * @param username 用户名（写进 sub，资源服务用它关联业务用户）
     * @param roles    角色名列表（不含 ROLE_ 前缀，如 ADMIN、USER）
     * @return 令牌响应（accessToken / tokenType / expiresIn）
     */
    public TokenResponse issue(String username, List<String> roles) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(properties.getTtlSeconds());

        // Header：声明算法与 kid。算法必须在头里写死为我们支持的那一种，解码侧也会核对。
        JwsHeader headers = JwsHeader.with(MacAlgorithm.HS256)
                .keyId(properties.getKeyId())
                .type("JWT")
                .build();

        // Payload：标准 claim（iss/sub/iat/exp/jti）+ 自定义 claim（roles）
        JwtClaimsSet claims = JwtClaimsSet.builder()
                // iss：谁签的。解码侧校验，防止别的系统用同一密钥签的令牌混进来
                .issuer(properties.getIssuer())
                // sub：令牌代表谁。这是**唯一稳定**的用户标识，业务侧用它查用户表
                .subject(username)
                // iat / exp：签发时间与过期时间（NimbusJwtDecoder 默认就会校验）
                .issuedAt(now)
                .expiresAt(expiresAt)
                // jti：令牌唯一 id，便于日志追踪与将来做「按 jti 拉黑」
                .id(UUID.randomUUID().toString())
                // roles：自定义 claim。注意它**不是** OAuth2 标准的 scope，
                // 所以资源服务器必须定制 JwtAuthenticationConverter 才能识别（本模块的经典坑）
                .claim(properties.getAuthoritiesClaim(), roles)
                .build();

        Jwt jwt = jwtEncoder.encode(JwtEncoderParameters.from(headers, claims));
        return new TokenResponse(jwt.getTokenValue(), "Bearer", properties.getTtlSeconds());
    }
}
