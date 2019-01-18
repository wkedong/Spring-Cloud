package com.wkedong.springcloud.serviceconsumer.ribbon.hystrix;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.client.circuitbreaker.EnableCircuitBreaker;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

/**
 * @author wkedong
 * 2019/1/5
 * Ribbon
 */
@EnableCircuitBreaker
@SpringBootApplication
@EnableDiscoveryClient
public class ServiceConsumerRibbonHystrixApplication {

    @Bean
    @LoadBalanced
    RestTemplate restTemplate() {
        return new RestTemplate();
    }

    public static void main(String[] args) {
        new SpringApplicationBuilder(ServiceConsumerRibbonHystrixApplication.class).web(true).run(args);
    }
}
