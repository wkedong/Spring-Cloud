package com.wkedong.boot.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 自定义 starter 的核心：自动配置类。
 * <p>
 * 三个关键条件注解（教学重点）：
 * <ul>
 *   <li>{@code @ConditionalOnProperty}：使用方可以用 basics.audit.enabled=false 一键关掉</li>
 *   <li>{@code @ConditionalOnMissingBean}：使用方自己定义了 AuditLogger 就**让位**（可覆盖原则）</li>
 *   <li>{@code @EnableConfigurationProperties}：把 @ConfigurationProperties 类注册成 bean 并完成绑定</li>
 * </ul>
 * 注册方式（Boot 2.7 两种都支持，3.0 起只认 imports 文件）：
 * <pre>
 * META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports   ← 推荐
 * META-INF/spring.factories 里的 org.springframework.boot.autoconfigure.EnableAutoConfiguration ← 旧写法
 * </pre>
 *
 * @author wkedong
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuditProperties.class)
@ConditionalOnProperty(prefix = "basics.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AuditAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AuditAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public AuditLogger auditLogger(AuditProperties properties) {
        // 这行 INFO 日志是故意留的：用来在教学时「看见」自动配置真的生效了
        log.info("basics-audit 自动配置生效：prefix={}, maxDetailLength={}, slowThreshold={}, bufferSize={}",
                properties.getPrefix(), properties.getMaxDetailLength(),
                properties.getSlowThreshold(), properties.getBufferSize());
        return new AuditLogger(properties);
    }
}
