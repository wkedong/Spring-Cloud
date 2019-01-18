package com.wkedong.springcloud.serviceconsumer.feign.service;

import com.alibaba.fastjson.JSONObject;
import org.springframework.cloud.netflix.feign.FeignClient;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * @author wkedong
 * FeignDemo
 * 2019/1/14
 */
@FeignClient("service-producer")
public interface ConsumerService {

    @RequestMapping("/provide")
    String consumer(JSONObject name);
}
