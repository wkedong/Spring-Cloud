package com.wkedong.springcloud.serviceproducer.controller;

import com.alibaba.fastjson.JSONObject;
import com.wkedong.springcloud.serviceproducer.service.ProducerService;
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

    @Autowired
    ProducerService producerService;

    @GetMapping(value = "/testGet")
    public String testGet() {
        return producerService.testGet();
    }

    @PostMapping(value = "/testPost")
    public String testPost(@RequestBody JSONObject jsonRequest) {
        return producerService.testPost(jsonRequest);
    }

    @PostMapping(value = "/testFile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String handleFileUpload(@RequestPart(value = "file") MultipartFile file) {
        return file.getOriginalFilename();
    }

    @GetMapping(value = "/testConfig")
    public String testConfig() {
        return producerService.testConfig();
    }

    @GetMapping(value = "/testRibbon")
    public String testRibbon() {
        return producerService.testRibbon();
    }

    @GetMapping(value = "/testFeign")
    public String testFeign() {
        return producerService.testFeign();
    }

    @GetMapping(value = "/testHystrix")
    public String testHystrix() {
        return producerService.testHystrix();
    }
}
