package com.wkedong.springcloud.serviceconsumer.ribbon.hystrix;

import org.springframework.boot.SpringApplication;
import io.micrometer.observation.ObservationRegistry;
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

    /**
     * 升级要点：{@code new RestTemplate()} 出来的客户端**不会**参与链路追踪。
     * <p>
     * Sleuth 时代是自动装饰 Client/RestTemplate 的，Micrometer Tracing 时代改为「观测驱动」：
     * 请求的 traceId 传播（写 traceparent/b3 头）与客户端 span 都由 ObservationRegistry 上的
     * observation 处理器完成，没有 registry 就没有传播头 —— 下游会另起一条新 trace
     * （本机实测：Zipkin 里 producer 侧出现独立 traceId）。
     * <p>
     * 另一条路是用 Boot 的 {@code RestTemplateBuilder}（自动挂 ObservationRestTemplateCustomizer），
     * 但 Boot 4 把 RestTemplateBuilder 拆到了独立模块 spring-boot-restclient，
     * 这里选择零新增依赖的写法：显式注入 ObservationRegistry。
     */
    @Bean
    @LoadBalanced
    RestTemplate restTemplate(ObservationRegistry observationRegistry) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setObservationRegistry(observationRegistry);
        return restTemplate;
    }

    public static void main(String[] args) {
        // web(true) 已废弃，Boot 2.x 默认即 SERVLET 应用，直接 run 即可
        SpringApplication.run(ServiceConsumerRibbonHystrixApplication.class, args);
    }
}
