package com.wkedong.springcloud.serviceconsumer.ribbon;

import org.springframework.boot.SpringApplication;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

/**
 * 带负载均衡的消费者（目录名沿用 ribbon 历史名，实现已换 LoadBalancer）
 * <p>
 * 2021.0.x 迁移说明：Ribbon 已移除，@LoadBalanced RestTemplate 的
 * 负载均衡由 Spring Cloud LoadBalancer 实现，业务代码无需改动。
 *
 * @author wkedong
 * 2019/1/5
 * Ribbon
 */
@SpringBootApplication
@EnableDiscoveryClient
public class ServiceConsumerRibbonApplication {

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
        SpringApplication.run(ServiceConsumerRibbonApplication.class, args);
    }
}