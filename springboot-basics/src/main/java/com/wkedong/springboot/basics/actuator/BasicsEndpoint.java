package com.wkedong.springboot.basics.actuator;

import com.wkedong.boot.audit.AuditLogger;
import com.wkedong.springboot.basics.config.BasicsProperties;
import com.wkedong.springboot.basics.service.AsyncTaskService;
import com.wkedong.springboot.basics.service.ProductService;
import com.wkedong.springboot.basics.task.ScheduledTasks;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 自定义 Actuator 端点：{@code GET /actuator/basics}。
 * <p>
 * 为什么用 @Endpoint 而不是再写一个 @RestController（教学点）：
 * <ul>
 *   <li>端点会自动继承 Actuator 的安全/暴露/脱敏策略（exposure.include 里加个名字即可）；</li>
 *   <li>支持 JMX 与 HTTP 双协议暴露（@Endpoint 是协议无关的，@RestController 只走 HTTP）；</li>
 *   <li>语义上「运维观测」与「业务接口」分开，访问日志与权限模型也不同。</li>
 * </ul>
 *
 * @author wkedong
 */
@Component
@Endpoint(id = "basics")
public class BasicsEndpoint {

    private final AuditLogger auditLogger;
    private final BasicsProperties properties;
    private final ScheduledTasks scheduledTasks;
    private final AsyncTaskService asyncTaskService;
    private final ProductService productService;
    private final CacheManager cacheManager;
    private final ThreadPoolTaskExecutor executor;

    public BasicsEndpoint(AuditLogger auditLogger, BasicsProperties properties, ScheduledTasks scheduledTasks,
                          AsyncTaskService asyncTaskService, ProductService productService,
                          CacheManager cacheManager, ThreadPoolTaskExecutor basicsExecutor) {
        this.auditLogger = auditLogger;
        this.properties = properties;
        this.scheduledTasks = scheduledTasks;
        this.asyncTaskService = asyncTaskService;
        this.productService = productService;
        this.cacheManager = cacheManager;
        this.executor = basicsExecutor;
    }

    @ReadOperation
    public Map<String, Object> overview() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("appName", properties.getAppName());
        data.put("environment", properties.getEnvironment());

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("totalRecords", auditLogger.totalRecords());
        audit.put("recent", auditLogger.recentRecords());
        data.put("audit", audit);

        Map<String, Object> scheduled = new LinkedHashMap<>();
        scheduled.put("fixedRateRuns", scheduledTasks.getFixedRateRuns());
        scheduled.put("lastFixedRateTime", scheduledTasks.getLastFixedRateTime());
        scheduled.put("cronRuns", scheduledTasks.getCronRuns());
        scheduled.put("lastCronTime", scheduledTasks.getLastCronTime());
        data.put("scheduledTasks", scheduled);

        Map<String, Object> async = new LinkedHashMap<>();
        async.put("executedTasks", asyncTaskService.executedCount());
        async.put("poolSize", executor.getPoolSize());
        async.put("activeCount", executor.getActiveCount());
        async.put("queueSize", executor.getThreadPoolExecutor().getQueue().size());
        data.put("asyncExecutor", async);

        Map<String, Object> cache = new LinkedHashMap<>();
        cache.put("realLoadCount", productService.getLoadCount());
        Cache products = cacheManager.getCache("products");
        if (products != null && products.getNativeCache() instanceof com.github.benmanes.caffeine.cache.Cache) {
            com.github.benmanes.caffeine.cache.Cache<?, ?> nativeCache =
                    (com.github.benmanes.caffeine.cache.Cache<?, ?>) products.getNativeCache();
            cache.put("caffeineStats", nativeCache.stats().toString());
        }
        data.put("cache", cache);
        return data;
    }
}
