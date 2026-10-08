package com.wkedong.springboot.basics;

import com.wkedong.boot.audit.AuditLogger;
import com.wkedong.springboot.basics.config.BasicsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 上下文启动测试 + 自定义 starter 自动配置验证。
 * <p>
 * 与本仓库其它模块的 {@code contextLoads} 不同：本模块不依赖注册中心/配置中心/数据库中间件，
 * 所以这个测试在 {@code mvn test} 里能**真实跑通**，可以直接作为教学演示。
 * 其它模块（需要 eureka/config）的同类测试标注了 @Disabled 并说明原因。
 *
 * @author wkedong
 */
@SpringBootTest(properties = {
        "basics.environment=from-test",
        "basics.retry.max-attempts=7",
        "basics.audit.prefix=[TEST-AUDIT]",
        "basics.cache.ttl=60s"
})
class SpringBootBasicsApplicationTests {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private BasicsProperties properties;

    /** 自定义 starter 自动装配出来的 bean：本模块从未 new 过它 */
    @Autowired
    private AuditLogger auditLogger;

    @Test
    void contextLoads() {
        assertThat(context).isNotNull();
    }

    @Test
    void starterAutoConfigurationWorks() {
        // 能被注入，说明 META-INF/spring/...AutoConfiguration.imports 生效
        assertThat(auditLogger).isNotNull();
        // 且 starter 的 @ConfigurationProperties 与当前环境完成绑定
        assertThat(auditLogger.currentProperties().getPrefix()).isEqualTo("[TEST-AUDIT]");
        assertThat(properties.getEnvironment()).isEqualTo("from-test");
        assertThat(properties.getRetry().getMaxAttempts()).isEqualTo(7);
    }

    @Test
    void auditLoggerRecordsToBuffer() {
        long before = auditLogger.totalRecords();
        auditLogger.record("单元测试", "验证审计记录写入");
        assertThat(auditLogger.totalRecords()).isEqualTo(before + 1);
        assertThat(auditLogger.recentRecords()).isNotEmpty();
        assertThat(auditLogger.recentRecords().get(0).getAction()).isEqualTo("单元测试");
    }
}
