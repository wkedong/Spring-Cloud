package com.wkedong.boot.audit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 自定义 starter 的配置项：basics.audit.*
 * <p>
 * 教学点：
 * 1. {@code @ConfigurationProperties} 支持**松散绑定**（yml 里的 kebab-case 自动映射到驼峰字段）；
 * 2. 用 {@link Duration} 接收 {@code 30s/500ms} 这类带单位的配置，比 long + 单位约定更安全；
 * 3. starter 的配置项要有**合理默认值**，使用方不配置也能用。
 *
 * @author wkedong
 */
@ConfigurationProperties(prefix = "basics.audit")
public class AuditProperties {

    /** 是否启用审计日志自动配置（false 时审计 bean 不会被创建） */
    private boolean enabled = true;

    /** 日志前缀，便于在日志里检索 */
    private String prefix = "[AUDIT]";

    /** 单条明细最大长度，超出截断，避免大字段把日志刷爆 */
    private int maxDetailLength = 200;

    /** 内存中保留的最近记录条数（教学演示用；生产应落库/投递消息） */
    private int bufferSize = 50;

    /** 日志级别：DEBUG/INFO/WARN，默认 INFO */
    private String level = "INFO";

    /** 记录超过该耗时（毫秒）的操作视为慢操作，打 WARN */
    private Duration slowThreshold = Duration.ofSeconds(1);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public int getMaxDetailLength() {
        return maxDetailLength;
    }

    public void setMaxDetailLength(int maxDetailLength) {
        this.maxDetailLength = maxDetailLength;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public Duration getSlowThreshold() {
        return slowThreshold;
    }

    public void setSlowThreshold(Duration slowThreshold) {
        this.slowThreshold = slowThreshold;
    }
}
