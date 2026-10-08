package com.wkedong.springcloud.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 服务网关
 * <p>
 * 2021.0.x 迁移说明：Zuul 1.x 已从 Spring Cloud 移除（Netflix 维护态），
 * 网关改由 Spring Cloud Gateway（基于 WebFlux 响应式栈）实现：
 * 1. @EnableZuulProxy / @SpringCloudApplication 移除，Gateway 无需开启注解；
 * 2. 依赖 spring-cloud-starter-zuul → spring-cloud-starter-gateway（内含 WebFlux，
 *    禁止再引入 spring-boot-starter-web）；
 * 3. 路由配置由 zuul.routes.* 迁移到 spring.cloud.gateway.routes.*，
 *    也可开启 discovery.locator 按服务 id 自动生成路由，见本模块 application.yml；
 * 4. 包名由 com.wkedong.springcloud.zuul 更名为 com.wkedong.springcloud.gateway，
 *    模块目录名沿用 zuul 历史名，避免破坏既有路径引用。
 *
 * @author wkedong
 */
@EnableDiscoveryClient
@SpringBootApplication
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
