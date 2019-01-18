package com.wkedong.springcloud.serviceconsumer.ribbon.controller;

import com.alibaba.fastjson.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

/**
 * @author wkedong
 * RobbinDemo
 * 2019/1/5
 */
@RestController
public class ConsumerController {

    @Autowired
    private RestTemplate restTemplate;

    @RequestMapping("/consumer")
    public String consumer(@RequestParam("name") String name) {

        JSONObject json = new JSONObject();
        json.put("name", name);

        //执行http请求Producer服务的provide映射地址，返回的数据为字符串类型
        //PRODUCER：提供者(Producer服务)的注册服务ID
        //provide ：消费方法
        return restTemplate.postForObject("http://service-producer/provide", json, String.class);
    }
}
