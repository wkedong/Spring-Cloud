package com.wkedong.springcloud.serviceconsumer.ribbon.hystrix.controller;

import com.wkedong.springcloud.serviceconsumer.ribbon.hystrix.service.HystrixService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author wkedong
 * RobbinDemo
 * 2019/1/5
 */
@RestController
public class HystrixController {
    // log4j 1.x → slf4j
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    HystrixService hystrixService;

    @GetMapping("/testHystrix")
    public String testHystrix() {
        logger.info("===<call testHystrix>===");
        return hystrixService.testHystrix();
    }
}
