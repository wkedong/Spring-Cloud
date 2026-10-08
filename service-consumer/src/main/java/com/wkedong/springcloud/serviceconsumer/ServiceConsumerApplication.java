package com.wkedong.springcloud.serviceconsumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

/**
 * 消费者（@LoadBalanced RestTemplate）
 * <p>
 * 2021.0.x 迁移说明：Ribbon 已移除，负载均衡由 Spring Cloud LoadBalancer
 * （配合 spring-cloud-starter-loadbalancer 依赖）实现，业务代码无需改动。
 *
 * @author wkedong
 * 2019/1/5
 * Consumer
 */
@SpringBootApplication
@EnableDiscoveryClient
public class ServiceConsumerApplication {

    @Bean
    @LoadBalanced
    RestTemplate restTemplate() {
        return new RestTemplate();
    }

    public static void main(String[] args) {
        // web(true) 已废弃，Boot 2.x 默认即 SERVLET 应用，直接 run 即可
        SpringApplication.run(ServiceConsumerApplication.class, args);
    }

}