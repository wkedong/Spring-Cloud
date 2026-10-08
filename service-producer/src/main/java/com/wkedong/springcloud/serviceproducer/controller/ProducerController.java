package com.wkedong.springcloud.serviceproducer.controller;

import com.alibaba.fastjson.JSONObject;
import com.wkedong.springcloud.serviceproducer.service.ProducerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
}
