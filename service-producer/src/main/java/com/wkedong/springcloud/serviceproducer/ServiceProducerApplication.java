package com.wkedong.springcloud.serviceproducer;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * @author wkedong
 */
@SpringBootApplication
@EnableDiscoveryClient
@MapperScan("com.wkedong.springcloud.serviceproducer.mapper")
public class ServiceProducerApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(ServiceProducerApplication.class).web(true).run(args);
    }

}

