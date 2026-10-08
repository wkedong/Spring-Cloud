package com.wkedong.springboot.basics.health;

import com.wkedong.boot.audit.AuditLogger;
import com.wkedong.springboot.basics.config.BasicsProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * 自定义健康检查：把「业务视角的健康」暴露给 /actuator/health。
 * <p>
 * 教学点：
 * <ul>
 *   <li>健康检查不是只有「进程活着」：要能表达依赖状态（本例用异步线程池积压率模拟）；</li>
 *   <li>{@code withDetail} 只在 {@code management.endpoint.health.show-details=always} 时对外可见；</li>
 *   <li>返回 DOWN 会让整个 /actuator/health 变成 503——K8s 就绪探针据此摘流量，
 *       所以判定条件要保守，避免「抖动即下线」。</li>
 * </ul>
 *
 * @author wkedong
 */
@Component("basics")
public class BasicsHealthIndicator implements HealthIndicator {

    private final ThreadPoolTaskExecutor executor;
    private final AuditLogger auditLogger;
    private final BasicsProperties properties;

    public BasicsHealthIndicator(ThreadPoolTaskExecutor basicsExecutor, AuditLogger auditLogger,
                                 BasicsProperties properties) {
        this.executor = basicsExecutor;
        this.auditLogger = auditLogger;
        this.properties = properties;
    }

    @Override
    public Health health() {
        int queueSize = executor.getThreadPoolExecutor().getQueue().size();
        int queueCapacity = 100;
        int activeCount = executor.getActiveCount();
        int poolSize = executor.getPoolSize();
        double saturation = Math.min(1.0d, (double) queueSize / queueCapacity);

        Health.Builder builder = saturation >= 0.9d ? Health.down() : Health.up();
        return builder
                .withDetail("environment", properties.getEnvironment())
                .withDetail("asyncPoolSize", poolSize)
                .withDetail("asyncActiveCount", activeCount)
                .withDetail("asyncQueueSize", queueSize)
                .withDetail("asyncQueueSaturation", String.format("%.0f%%", saturation * 100))
                .withDetail("auditRecords", auditLogger.totalRecords())
                .withDetail("hint", "队列积压 ≥90% 判定为 DOWN；生产可替换为对下游依赖的真实探测")
                .build();
    }
}
