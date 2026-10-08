package com.wkedong.springcloud.serviceproducer.controller;

import com.alibaba.fastjson.JSONObject;
import com.wkedong.springcloud.serviceproducer.service.ProducerService;
import com.wkedong.springcloud.serviceproducer.service.SpanAnnotatedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author wkedong
 * <p>
 * 2019/1/14
 */
@RestController
public class ProducerController {

    // log4j 1.x 已 EOL，Boot 2.x 默认日志门面/实现为 slf4j + Logback
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    ProducerService producerService;

    @Autowired
    SpanAnnotatedService spanAnnotatedService;

    @GetMapping(value = "/testGet")
    public String testGet() {
        logger.info("===<call testGet>===");
        return producerService.testGet();
    }

    @PostMapping(value = "/testPost")
    public String testPost(@RequestBody JSONObject jsonRequest) {
        logger.info("===<call testPost>===");
        return producerService.testPost(jsonRequest);
    }

    @PostMapping(value = "/testFile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String handleFileUpload(@RequestPart(value = "file") MultipartFile file) {
        logger.info("===<call testFile>===");
        return file.getOriginalFilename();
    }

    @GetMapping(value = "/testConfig")
    public String testConfig() {
        logger.info("===<call testConfig>===");
        return producerService.testConfig();
    }

    @GetMapping(value = "/testRibbon")
    public String testRibbon() {
        logger.info("===<call testRibbon>===");
        return producerService.testRibbon();
    }

    @GetMapping(value = "/testFeign")
    public String testFeign() {
        logger.info("===<call testFeign>===");
        return producerService.testFeign();
    }

    @GetMapping(value = "/testHystrix")
    public String testHystrix() {
        logger.info("===<call testHystrix>===");
        return producerService.testHystrix();
    }

    // ======================= 教学扩展端点 =======================

    /** 回显所有请求头：验证 Feign 的 RequestInterceptor 与 Sleuth 头透传 */
    @GetMapping(value = "/echoHeaders")
    public Map<String, Object> echoHeaders(@RequestHeader Map<String, String> headers) {
        return producerService.echoHeaders(headers);
    }

    /** 回显实例身份与元数据：灰度路由（按 metadata.version 选实例）的证据来源 */
    @GetMapping(value = "/echoInstance")
    public Map<String, Object> echoInstance() {
        return producerService.echoInstance();
    }

    /** 故意返回 500：用于演示 Feign 自定义 ErrorDecoder 与 Resilience4j/Sentinel 的失败统计 */
    @GetMapping(value = "/testError")
    public ResponseEntity<Map<String, Object>> testError() {
        logger.info("===<call testError>=== 故意返回 500");
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("error", "PRODUCER_INTERNAL_ERROR");
        body.put("code", 50001);
        body.put("message", "演示用下游错误：库存服务不可用");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    /** 慢接口：演示 Feign 读超时、Sentinel 慢调用比例熔断 */
    @GetMapping(value = "/testSlow")
    public String testSlow(@RequestParam(value = "seconds", defaultValue = "5") int seconds) {
        return producerService.testSlow(seconds);
    }

    /** 首次失败、之后成功：演示 LoadBalancer 重试确实重发了请求 */
    @GetMapping(value = "/testRetry")
    public ResponseEntity<Map<String, Object>> testRetry() {
        Map<String, Object> data = producerService.testRetry();
        boolean success = Boolean.TRUE.equals(data.get("success"));
        return success ? ResponseEntity.ok(data) : ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(data);
    }

    /** 配置刷新对照：@RefreshScope vs 普通 @Value */
    @GetMapping(value = "/testConfigRefresh")
    public Map<String, Object> testConfigRefresh() {
        return producerService.configRefreshDemo();
    }

    /** 自定义 span：同时演示编程式与注解式两种写法 */
    @GetMapping(value = "/testSpan")
    public String testSpan(@RequestParam(value = "tag", defaultValue = "gray-v2") String tag) {
        logger.info("===<call testSpan>=== tag={}", tag);
        spanAnnotatedService.annotatedWork(tag);
        return producerService.spanDemo(tag);
    }
}
