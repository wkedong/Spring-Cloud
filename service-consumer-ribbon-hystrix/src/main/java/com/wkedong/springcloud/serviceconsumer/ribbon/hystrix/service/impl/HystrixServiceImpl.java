package com.wkedong.springcloud.serviceconsumer.ribbon.hystrix.service.impl;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.wkedong.springcloud.serviceconsumer.ribbon.hystrix.service.HystrixService;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * @author wkedong
 * Hystrix
 * 2019/1/15
 */
@Service
public class HystrixServiceImpl implements HystrixService {

    private final Logger logger = Logger.getLogger(getClass());

    @Autowired
    RestTemplate restTemplate;

    @Override
    @HystrixCommand(fallbackMethod = "fallback")
    public String testHystrix() {
        //执行http请求service-producer服务的testHystrix映射地址，返回的数据为字符串类型
        //服务提供者(service-producer服务)的注册服务ID
        //testHystrix ：消费方法
        return restTemplate.getForObject("http://service-producer/testHystrix", String.class);
    }

    public String fallback() {
        logger.info("===<call testHystrix fail fallback>===");
        return "service-producer /testHystrix is error";
    }
}
