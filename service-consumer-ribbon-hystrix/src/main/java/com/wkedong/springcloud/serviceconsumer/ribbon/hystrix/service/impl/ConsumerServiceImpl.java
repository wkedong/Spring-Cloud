package com.wkedong.springcloud.serviceconsumer.ribbon.hystrix.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.wkedong.springcloud.serviceconsumer.ribbon.hystrix.service.ConsumerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;

/**
 * @author wkedong
 * Hystrix
 * 2019/1/15
 */
@Service
public class ConsumerServiceImpl implements ConsumerService {

    @Autowired
    RestTemplate restTemplate;

    @Override
    @HystrixCommand(fallbackMethod = "fallback")
    public String consumer(@RequestParam("name") String name) {

        JSONObject json = new JSONObject();
        json.put("name", name);

        //执行http请求Producer服务的provide映射地址，返回的数据为字符串类型
        //PRODUCER：提供者(Producer服务)的注册服务ID
        //provide ：消费方法
        return restTemplate.postForObject("http://service-producer/provide", json, String.class);
    }

    public String fallback(@RequestParam("name") String name) {
        return "fallback" + name;
    }
}
