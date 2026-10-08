package com.wkedong.springcloud.serviceconsumer.feign;

import feign.codec.Encoder;
import feign.form.spring.SpringFormEncoder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign 声明式服务消费者
 * <p>
 * 2021.0.x 迁移说明：
 * 1. starter 更名 spring-cloud-starter-feign → spring-cloud-starter-openfeign；
 * 2. @EnableFeignClients 包由 org.springframework.cloud.netflix.feign
 *    迁移到 org.springframework.cloud.openfeign；
 * 3. 需引入 spring-cloud-starter-loadbalancer 提供服务间负载均衡。
 *
 * @author wkedong
 */
@EnableFeignClients
@EnableDiscoveryClient
@SpringBootApplication
public class ServiceConsumerFeignApplication {

    public static void main(String[] args) {
        // web(true) 已废弃，Boot 2.x 默认即 SERVLET 应用，直接 run 即可
        SpringApplication.run(ServiceConsumerFeignApplication.class, args);
    }

    /**
     * multipart/form-data 编码器（static 内部类，Boot 2.x 对非 static 内部 @Configuration 会告警）
     */
    @Configuration
    static class MultipartSupportConfig {
        @Bean
        public Encoder feignFormEncoder() {
            return new SpringFormEncoder();
        }
    }
}
