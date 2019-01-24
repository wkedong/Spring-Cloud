package com.wkedong.springcloud.serviceconsumer.ribbon.hystrix.controller;

import com.wkedong.springcloud.serviceconsumer.ribbon.hystrix.service.HystrixService;
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

    @Autowired
    HystrixService hystrixService;

    @GetMapping("/testHystrix")
    public String testHystrix() {

        return hystrixService.testHystrix();
    }
}
