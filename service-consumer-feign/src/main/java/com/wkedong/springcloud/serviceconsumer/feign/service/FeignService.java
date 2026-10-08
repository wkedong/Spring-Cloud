package com.wkedong.springcloud.serviceconsumer.feign.service;

import com.wkedong.springcloud.serviceconsumer.feign.config.ProducerFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;


/**
 * @author wkedong
 * FeignDemo
 * 2019/1/14
 */
@FeignClient(name = "service-producer", configuration = ProducerFeignConfig.class,
        fallbackFactory = FeignServiceFallbackFactory.class)
public interface FeignService {

    @GetMapping(value = "/testFeign")
    String testFeign();

    @PostMapping(value = "/testFile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    String testFeignFile(@RequestPart(value = "file") MultipartFile file);

    // ================= 教学扩展（对应 docs/17 Feign 进阶） =================

    /** 回显请求头：验证 RequestInterceptor 注入的 X-From / X-Request-Id 与 Sleuth 透传的 X-B3-* */
    @GetMapping(value = "/echoHeaders")
    Map<String, Object> echoHeaders();

    /** 下游慢接口：验证 feign.client.config.default.readTimeout 生效（读超时抛异常） */
    @GetMapping(value = "/testSlow")
    String testSlow(@RequestParam("seconds") int seconds);

    /** 下游 5xx：验证自定义 ErrorDecoder 把它翻译成 DownstreamServiceException */
    @GetMapping(value = "/testError")
    String testError();
}
