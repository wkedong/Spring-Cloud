package com.wkedong.springcloud.serviceconsumer.feign.service;

import feign.codec.Encoder;
import feign.form.spring.SpringFormEncoder;
import org.springframework.cloud.netflix.feign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;


/**
 * @author wkedong
 * FeignDemo
 * 2019/1/14
 */
@FeignClient("service-producer")
public interface FeignService {

    @GetMapping(value = "/testFeign")
    String testFeign();

    @PostMapping(value = "/testFile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    String testFeignFile(@RequestPart(value = "file") MultipartFile file);

    @Configuration
    class MultipartSupportConfig {
        @Bean
        public Encoder feignFormEncoder() {
            return new SpringFormEncoder();
        }
    }
}
