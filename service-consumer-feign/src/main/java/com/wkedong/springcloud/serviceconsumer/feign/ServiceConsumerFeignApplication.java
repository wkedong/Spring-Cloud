package com.wkedong.springcloud.serviceconsumer.feign;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.netflix.feign.EnableFeignClients;

/**
 * @author wkedong
 */
@EnableFeignClients
@EnableDiscoveryClient
@SpringBootApplication
public class ServiceConsumerFeignApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(ServiceConsumerFeignApplication.class).web(true).run(args);
    }

}
