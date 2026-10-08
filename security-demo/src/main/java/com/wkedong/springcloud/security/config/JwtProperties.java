package com.wkedong.springcloud.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;

/**
 * JWT 相关配置：{@code security.jwt.*}
 * <p>
 * 为什么把密钥做成配置项而不是写死在代码里：
 * <ol>
 *   <li><b>同一份包要能跑多个环境</b>：dev/测试/生产的密钥必须不同，写死意味着改环境要重新打包；</li>
 *   <li><b>密钥一旦进 Git 就等于泄露</b>：代码仓库会被 clone、被打包、被日志打印，密钥没有「内部」可言；</li>
 *   <li><b>泄露后要能快速轮换</b>：放在配置里（环境变量 / 配置中心加密项）才可能做到「不重新发版就换密钥」。</li>
 * </ol>
 * 本模块用 {@code ${SECURITY_JWT_SECRET:...}} 给它一个教学默认值：单机可跑，但注释明确写了生产必须覆盖。
 *
 * @author wkedong
 */
@ConfigurationProperties(prefix = "security.jwt")
@Validated
public class JwtProperties {

    /** HS256 要求密钥长度至少 256 位（32 字节），短于此值启动即失败，避免「演示能跑、上线被打」 */
    public static final int MIN_SECRET_BYTES = 32;

    /** 对称密钥（教学默认值来自 yml，生产用环境变量 SECURITY_JWT_SECRET 覆盖） */
    @NotBlank
    private String secret;

    /** 签发者 iss：解码侧用同一值校验，防止拿到别处签发的令牌 */
    @NotBlank
    private String issuer = "security-demo";

    /** 令牌有效期（秒） */
    @Min(60)
    private long ttlSeconds = 3600L;

    /** 承载角色的 claim 名。默认 roles；用 OAuth2 标准 scope 时权限会带 SCOPE_ 前缀，hasRole 就失效了 */
    @NotBlank
    private String authoritiesClaim = "roles";

    /** 权限前缀：hasRole('ADMIN') 比对的是 ROLE_ADMIN，所以映射时要补上前缀 */
    private String authorityPrefix = "ROLE_";

    /** 教学开关：true 表示故意使用 Spring Security 默认的 claim 映射器（只认 scope → SCOPE_） */
    private boolean useDefaultConverter = false;

    /** 密钥 id，写进 JWT 头部的 kid；将来做密钥轮换时，解码端可据此选不同密钥 */
    private String keyId = "security-demo-hmac-key";

    /**
     * 取密钥字节。这里显式校验长度：与其让攻击者慢慢爆破弱密钥，不如启动就报错。
     *
     * @return UTF-8 字节
     */
    public byte[] secretBytes() {
        byte[] bytes = this.secret == null ? new byte[0] : this.secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("security.jwt.secret 长度不足：" + bytes.length
                    + " 字节，HS256 至少需要 " + MIN_SECRET_BYTES + " 字节（256 位）。"
                    + "请用环境变量 SECURITY_JWT_SECRET 注入一个足够长的随机串。");
        }
        return bytes;
    }

    /**
     * 构造 JCA 密钥对象。算法名写成 {@code HmacSHA256}，JCE 才会按 HMAC-SHA256 处理。
     *
     * @return 对称密钥
     */
    public SecretKey secretKey() {
        return new SecretKeySpec(secretBytes(), "HmacSHA256");
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public String getAuthoritiesClaim() {
        return authoritiesClaim;
    }

    public void setAuthoritiesClaim(String authoritiesClaim) {
        this.authoritiesClaim = authoritiesClaim;
    }

    public String getAuthorityPrefix() {
        return authorityPrefix;
    }

    public void setAuthorityPrefix(String authorityPrefix) {
        this.authorityPrefix = authorityPrefix;
    }

    public boolean isUseDefaultConverter() {
        return useDefaultConverter;
    }

    public void setUseDefaultConverter(boolean useDefaultConverter) {
        this.useDefaultConverter = useDefaultConverter;
    }

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }
}
