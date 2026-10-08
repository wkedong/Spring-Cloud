package com.wkedong.springboot.basics.web;

import com.wkedong.boot.audit.AuditLogger;
import com.wkedong.springboot.basics.config.BasicsProperties;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置体系演示接口。
 * <ul>
 *   <li>{@code GET /api/config} —— 当前生效的配置（含 starter 的 basics.audit.*）</li>
 *   <li>{@code GET /api/config/priority} —— 配置来源与优先级（谁覆盖了谁）</li>
 *   <li>{@code GET /api/config/features/{name}} —— 功能开关读取</li>
 * </ul>
 *
 * @author wkedong
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final BasicsProperties properties;
    private final AuditLogger auditLogger;
    private final Environment environment;
    private final ConfigurableEnvironment configurableEnvironment;

    public ConfigController(BasicsProperties properties, AuditLogger auditLogger,
                            Environment environment, ConfigurableEnvironment configurableEnvironment) {
        this.properties = properties;
        this.auditLogger = auditLogger;
        this.environment = environment;
        this.configurableEnvironment = configurableEnvironment;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> current() {
        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> basics = new LinkedHashMap<>();
        basics.put("appName", properties.getAppName());
        basics.put("environment", properties.getEnvironment());
        basics.put("securityToken", properties.getSecurityToken());
        basics.put("features", properties.getFeatures());
        basics.put("contacts", properties.getContacts());
        basics.put("retry.maxAttempts", properties.getRetry().getMaxAttempts());
        basics.put("retry.backoff", properties.getRetry().getBackoff().toString());
        basics.put("cache.ttl", properties.getCache().getTtl().toString());
        basics.put("cache.maxSize", properties.getCache().getMaxSize());
        data.put("basics", basics);

        // 这部分来自自定义 starter 的 @ConfigurationProperties（basics.audit.*）
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("prefix", auditLogger.currentProperties().getPrefix());
        audit.put("maxDetailLength", auditLogger.currentProperties().getMaxDetailLength());
        audit.put("slowThreshold", auditLogger.currentProperties().getSlowThreshold().toString());
        audit.put("level", auditLogger.currentProperties().getLevel());
        data.put("basicsAuditFromStarter", audit);

        data.put("activeProfiles", environment.getActiveProfiles());
        return ApiResponse.ok(data);
    }

    /**
     * 配置优先级演示：列出所有「含 basics 前缀配置项」的属性源，按 Spring 的查找顺序排列。
     * <p>
     * 前面的源优先级更高（命令行参数 &gt; 环境变量 &gt; profile 文件 &gt; 默认文件）。
     * 可以用命令行参数验证：{@code --basics.environment=from-cli} 会盖住 yml 里的值。
     */
    @GetMapping("/priority")
    public ApiResponse<Map<String, Object>> priority() {
        Map<String, Object> data = new LinkedHashMap<>();
        List<Map<String, Object>> sources = new ArrayList<>();
        for (PropertySource<?> source : configurableEnvironment.getPropertySources()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", source.getName());
            if (source instanceof EnumerablePropertySource) {
                EnumerablePropertySource<?> enumerable = (EnumerablePropertySource<?>) source;
                Map<String, Object> matched = new LinkedHashMap<>();
                for (String name : enumerable.getPropertyNames()) {
                    if (name.startsWith("basics.") && !name.startsWith("basics.audit.")) {
                        matched.put(name, source.getProperty(name));
                    }
                }
                if (matched.isEmpty()) {
                    continue;
                }
                item.put("basicsKeys", matched);
            } else {
                continue;
            }
            sources.add(item);
        }
        data.put("orderedSources", sources);
        data.put("hint", "列表按优先级从高到低；命令行参数与环境变量排在最前，会覆盖 yml 中的同名配置");
        data.put("resolvedEnvironment", properties.getEnvironment());
        return ApiResponse.ok(data);
    }

    @GetMapping("/features/{name}")
    public ApiResponse<Map<String, Object>> feature(@PathVariable("name") String name) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("feature", name);
        data.put("enabled", properties.getFeatures().getOrDefault(name, Boolean.FALSE));
        data.put("allFeatures", properties.getFeatures());
        return ApiResponse.ok(data);
    }
}
