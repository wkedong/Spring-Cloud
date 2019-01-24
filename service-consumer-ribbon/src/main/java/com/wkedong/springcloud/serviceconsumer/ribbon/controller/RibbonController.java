package com.wkedong.springcloud.serviceconsumer.ribbon.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

/**
 * @author wkedong
 * RobbinDemo
 * 2019/1/5
 */
@RestController
public class RibbonController {

    @Autowired
    private RestTemplate restTemplate;

    @GetMapping(value = "/testRibbon")
    public String testRibbon() {

        //执行http请求Producer服务的provide映射地址，返回的数据为字符串类型
        //PRODUCER：提供者(Producer服务)的注册服务ID
        //provide ：消费方法
        return restTemplate.getForObject("http://service-producer/testRibbon", String.class);
    }
}
