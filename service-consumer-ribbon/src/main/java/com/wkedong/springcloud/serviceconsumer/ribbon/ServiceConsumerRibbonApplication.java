package com.wkedong.springcloud.serviceconsumer.ribbon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

/**
 * 带负载均衡的消费者（目录名保留 ribbon 历史名，以便与 master 分支对照）
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

    @Bean
    @LoadBalanced
    RestTemplate restTemplate() {
        return new RestTemplate();
    }

    public static void main(String[] args) {
        // web(true) 已废弃，Boot 2.x 默认即 SERVLET 应用，直接 run 即可
        SpringApplication.run(ServiceConsumerRibbonApplication.class, args);
    }
}