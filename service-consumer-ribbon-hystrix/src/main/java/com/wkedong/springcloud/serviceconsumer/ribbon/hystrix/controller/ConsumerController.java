package com.wkedong.springcloud.serviceconsumer.ribbon.hystrix.controller;

import com.wkedong.springcloud.serviceconsumer.ribbon.hystrix.service.ConsumerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author wkedong
 * RobbinDemo
 * 2019/1/5
 */
@RestController
public class ConsumerController {

    @Autowired
    ConsumerService consumerService;

    @RequestMapping("/consumer")
    public String consumer(@RequestParam("name") String name) {

        return consumerService.consumer(name);
    }
}
