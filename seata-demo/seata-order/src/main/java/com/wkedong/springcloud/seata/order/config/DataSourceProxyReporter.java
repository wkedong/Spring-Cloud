package com.wkedong.springcloud.seata.order.config;

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
 * <p>{@code dataSource.getClass()} 打印出来是 {@code HikariDataSource$$EnhancerBySpringCGLIB$$xxx}，
 * 这是 Seata 1.6.x {@code SeataAutoDataSourceProxyCreator}（AOP 方式）套的代理，
 * 真正的 {@code DataSourceProxy} 只在全局事务上下文里才换进去，
 * 所以要凭 advice 判断，而不是凭类名。</p>
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
