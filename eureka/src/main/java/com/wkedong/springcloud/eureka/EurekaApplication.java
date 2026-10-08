package com.wkedong.springcloud.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Eureka 注册中心
 * <p>
 * 2021.0.x 迁移说明：starter 由 spring-cloud-starter-eureka-server
 * 更名为 spring-cloud-starter-netflix-eureka-server，注解与启动方式不变。
 *
 * @author wkedong
 */
@EnableEurekaServer
@SpringBootApplication
public class EurekaApplication {

    public static void main(String[] args) {
        // 原 new SpringApplicationBuilder(X.class).web(true).run(args) 的
        // web(true) 已废弃，Boot 2.x 默认即 SERVLET 应用，直接 run 即可
        SpringApplication.run(EurekaApplication.class, args);
    }

}
