package com.wkedong.springcloud.seata.inventory.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * 启动时判断「数据源到底有没有被 Seata 代理」——AT 模式能不能生效全看这一点。
 *
 * <p>两个容易误判的地方（实测踩过）：</p>
 * <ol>
 *   <li>{@code dataSource.getClass()} 打印出来是 {@code HikariDataSource$$EnhancerBySpringCGLIB$$xxx}，
 *       看不到 {@code DataSourceProxy} 的字样，让人以为没生效。其实这正是 Seata 的 AOP 代理：
 *       1.6.x 的 {@code SeataAutoDataSourceProxyCreator} 用 {@code AbstractAutoProxyCreator}
 *       在 {@code DataSource} 外面套了一层代理，真正的 {@code DataSourceProxy} 是调用时才换进去的。
 *       所以要看的是「代理里的 advice 是谁」。</li>
 *   <li>代理的 advice 只在全局事务上下文里才生效（{@code RootContext.requireGlobalLock() ||
 *       inGlobalTransaction()}）。**在全局事务外拿到的连接本来就该是原生连接**，
 *       不能拿它当「AT 没生效」的证据。</li>
 * </ol>
 *
 * @author wkedong
 */
@Configuration
public class DataSourceProxyReporter {

    private static final Logger log = LoggerFactory.getLogger(DataSourceProxyReporter.class);

    @Bean
    public ApplicationRunner seataDataSourceProxyReport(DataSource dataSource) throws SQLException {
        return args -> {
            log.info("DataSource bean 类型：{}", dataSource.getClass().getName());
            log.info("AOP 代理={}, 目标类型={}", AopUtils.isAopProxy(dataSource),
                    AopUtils.getTargetClass(dataSource).getName());
            if (dataSource instanceof Advised) {
                for (org.springframework.aop.Advisor advisor : ((Advised) dataSource).getAdvisors()) {
                    log.info("数据源上的 advice：{}", advisor.getAdvice().getClass().getName());
                }
            }
            try (Connection connection = dataSource.getConnection()) {
                log.info("全局事务外连接类型：{}（此时是原生连接属正常，全局事务内会换成 ConnectionProxy）",
                        connection.getClass().getName());
            }
        };
    }
}
