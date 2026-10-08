package com.wkedong.springcloud.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * 配置中心（JDBC 后端）
 * <p>
 * 2021.0.x 迁移说明：
 * 1. starter 更名 spring-cloud-starter-eureka-server → netflix-eureka-server；
 * 2. Flyway 配置前缀 flyway.* → spring.flyway.*，并需新增 flyway-mysql 依赖；
 * 3. MySQL 驱动升级 8.0，驱动类更名 com.mysql.cj.jdbc.Driver。
 *
 * @author wkedong
 */
@EnableDiscoveryClient
@EnableConfigServer
@SpringBootApplication
public class ConfigApplication {

    public static void main(String[] args) {
        // web(true) 已废弃，Boot 2.x 默认即 SERVLET 应用，直接 run 即可
        SpringApplication.run(ConfigApplication.class, args);
    }
}
