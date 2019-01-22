package com.wkedong.springcloud.serviceproducer.controller;

import com.alibaba.fastjson.JSONObject;
import com.wkedong.springcloud.serviceproducer.service.ProducerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author wkedong
 * <p>
 * 2019/1/14
 */
@RestController
public class ProducerController {

    @Value("${name}")
    private String name;

    @Autowired
    ProducerService producerService;

    @RequestMapping(value = "/provide", method = RequestMethod.POST)
    public String producer(@RequestBody JSONObject jsonRequest) {
        return producerService.producer(jsonRequest);
    }

    @RequestMapping(value = "/test", method = RequestMethod.GET)
    public String test() {
        return producerService.test();
    }

    @RequestMapping(value = "/getConfig", method = RequestMethod.GET)
    public String getConfig() {
        return name;
    }
}
