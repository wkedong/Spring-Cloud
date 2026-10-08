package com.wkedong.springcloud.serviceconsumer.ribbon.hystrix;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

/**
 * 带熔断机制的消费者（目录名沿用 ribbon-hystrix 历史名，实现已换 Resilience4j）
 * <p>
 * 2021.0.x 迁移说明：Hystrix 进入维护态并已从 Spring Cloud 移除，
 * 熔断改由 Spring Cloud CircuitBreaker 抽象 + Resilience4j 实现承担：
 * 1. @EnableHystrix / @EnableHystrixDashboard 无需保留（CircuitBreakerFactory 自动装配）；
 * 2. @HystrixCommand(fallbackMethod) 改为 CircuitBreakerFactory.run(supplier, fallback)，
 *    见 HystrixServiceImpl；
 * 3. Hystrix Dashboard 由 actuator 的 /actuator/circuitbreakers、
 *    /actuator/circuitbreakerevents 端点替代观察。
 *
 * @author wkedong
 * 2019/1/5
 * Ribbon
 */
@SpringBootApplication
@EnableDiscoveryClient
public class ServiceConsumerRibbonHystrixApplication {

    @Bean
    @LoadBalanced
    RestTemplate restTemplate() {
        return new RestTemplate();
    }

    public static void main(String[] args) {
        // web(true) 已废弃，Boot 2.x 默认即 SERVLET 应用，直接 run 即可
        SpringApplication.run(ServiceConsumerRibbonHystrixApplication.class, args);
    }
}
