package com.wkedong.springcloud.security.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;

/**
 * JWT 编解码器配置：用 Spring Security 官方 API（Nimbus 实现），不引第三方 JWT 库。
 * <p>
 * 为什么用官方 API 而不是 jjwt 之类的工具包：
 * <ol>
 *   <li><b>依赖已经在了</b>：只要引入 spring-boot-starter-oauth2-resource-server（做资源服务器本来就要），
 *       {@code spring-security-oauth2-jose} 就带进来了，Nimbus 编解码器开箱可用；</li>
 *   <li><b>签发与校验是同一套 API</b>：{@link JwtEncoder} / {@link JwtDecoder} 是接口，
 *       今天用对称密钥、明天换 RSA/远程 JWK Set，只换 Bean 的构造代码，业务代码零改动；</li>
 *   <li><b>安全性由框架兜底</b>：NimbusJwtDecoder 默认开启时间戳校验（exp/nbf），
 *       自己手撸解析很容易忘记校验过期时间——这是 JWT 最经典的高危漏洞。</li>
 * </ol>
 *
 * @author wkedong
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    /**
     * 签发器：对称密钥（HS256）。
     * <p>
     * 这里刻意走「JWK（JSON Web Key）」这条路而不是直接用 byte[]：
     * JWK 是 JWT 生态里的标准密钥描述格式，带上 {@code kid} 与 {@code alg} 后，
     * 将来换成 RSA、或变成配置中心下发的「一组密钥」时，代码结构不用变（只需换 JWKSet 的内容）。
     *
     * @param properties JWT 配置
     * @return JWT 签发器
     */
    @Bean
    public JwtEncoder jwtEncoder(JwtProperties properties) {
        SecretKey secretKey = properties.secretKey();
        OctetSequenceKey jwk = new OctetSequenceKey.Builder(properties.secretBytes())
                .algorithm(JWSAlgorithm.HS256)
                .keyID(properties.getKeyId())
                .build();
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(jwk));
        return new NimbusJwtEncoder(jwkSource);
    }

    /**
     * 解码器：与签发侧共用同一把对称密钥。
     * <p>
     * 注意两件事：
     * <ol>
     *   <li>必须显式声明 {@code macAlgorithm(HS256)}：解码器需要知道「允许哪些算法」，
     *       否则无法判断令牌是不是用了我们允许的算法签发（算法混淆攻击的防线之一）；</li>
     *   <li>必须设置校验器：默认只校验时间戳，{@code createDefaultWithIssuer} 额外校验 iss，
     *       这样「别的系统用同一把密钥签的令牌」也不能拿来访问本服务。</li>
     * </ol>
     *
     * @param properties JWT 配置
     * @return JWT 解码器
     */
    @Bean
    public JwtDecoder jwtDecoder(JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(properties.secretKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        // 校验：exp/nbf 时间戳 + iss 签发者
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.getIssuer()));
        return decoder;
    }
}
