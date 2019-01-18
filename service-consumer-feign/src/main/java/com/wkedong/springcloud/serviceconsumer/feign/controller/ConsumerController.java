package com.wkedong.springcloud.serviceconsumer.feign.controller;

import com.alibaba.fastjson.JSONObject;
import com.wkedong.springcloud.serviceconsumer.feign.service.ConsumerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author wkedong
 * FeignDemo
 * 2019/1/14
 */
@RestController
public class ConsumerController {

    @Autowired
    ConsumerService consumerService;

    @RequestMapping("/consumer")
    public String consumer(@RequestParam("name") String name) {

        JSONObject json = new JSONObject();
        json.put("name", name);

        return consumerService.consumer(json);
    }

}
