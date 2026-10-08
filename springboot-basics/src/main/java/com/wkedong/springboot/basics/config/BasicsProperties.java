package com.wkedong.springboot.basics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本模块的业务配置：basics.*
 * <p>
 * 与 {@code @Value} 相比，{@code @ConfigurationProperties} 的四个优势（教学重点）：
 * <ol>
 *   <li><b>松散绑定</b>：yml 写 {@code app-name}，Java 字段用 {@code appName}</li>
 *   <li><b>类型安全</b>：直接绑定 Duration / List / Map / 嵌套对象，不用手写转换</li>
 *   <li><b>可校验</b>：类上加 {@code @Validated} + JSR-303 注解，**启动时**就发现配置错误</li>
 *   <li><b>可元数据化</b>：配合 configuration-processor，IDE 里有补全与提示</li>
 * </ol>
 *
 * @author wkedong
 */
@ConfigurationProperties(prefix = "basics")
@Validated
public class BasicsProperties {

    /** 应用展示名（@NotBlank：缺失会导致启动失败，这是「配置校验失败快速暴露」的教学示例） */
    @NotBlank
    private String appName = "springboot-basics";

    /** 当前环境标识，可用环境变量 BASICS_ENV 覆盖 */
    private String environment = "local";

    /** 接口鉴权 token（演示用；生产应换成 JWT/OAuth2） */
    private String securityToken = "dev-token";

    /** 功能开关：Map 结构演示「一个配置控制多个特性」 */
    private Map<String, Boolean> features = new LinkedHashMap<>();

    /** 通知联系人列表 */
    private List<String> contacts = new ArrayList<>();

    /** 重试策略（嵌套对象） */
    private Retry retry = new Retry();

    /** 缓存策略 */
    private Cache cache = new Cache();

    public static class Retry {
        /** 最大尝试次数 */
        @Min(1)
        private int maxAttempts = 3;
        /** 退避间隔，支持 200ms / 2s 等写法 */
        private Duration backoff = Duration.ofMillis(200);

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getBackoff() {
            return backoff;
        }

        public void setBackoff(Duration backoff) {
            this.backoff = backoff;
        }
    }

    public static class Cache {
        /** 写入后过期时间 */
        private Duration ttl = Duration.ofSeconds(5);
        /** 最大条目数 */
        private long maxSize = 100;

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }

        public long getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(long maxSize) {
            this.maxSize = maxSize;
        }
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getSecurityToken() {
        return securityToken;
    }

    public void setSecurityToken(String securityToken) {
        this.securityToken = securityToken;
    }

    public Map<String, Boolean> getFeatures() {
        return features;
    }

    public void setFeatures(Map<String, Boolean> features) {
        this.features = features;
    }

    public List<String> getContacts() {
        return contacts;
    }

    public void setContacts(List<String> contacts) {
        this.contacts = contacts;
    }

    public Retry getRetry() {
        return retry;
    }

    public void setRetry(Retry retry) {
        this.retry = retry;
    }

    public Cache getCache() {
        return cache;
    }

    public void setCache(Cache cache) {
        this.cache = cache;
    }
}
