package com.wkedong.springcloud.serviceproducer;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * @author wkedong
 */
@SpringBootApplication
@EnableDiscoveryClient
@MapperScan("com.wkedong.springcloud.serviceproducer.mapper")
public class ServiceProducerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceProducerApplication.class, args);
    }

}

