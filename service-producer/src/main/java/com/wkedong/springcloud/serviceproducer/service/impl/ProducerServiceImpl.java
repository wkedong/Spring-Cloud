package com.wkedong.springcloud.serviceproducer.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.netflix.appinfo.EurekaInstanceConfig;
import com.wkedong.springcloud.serviceproducer.entity.UserEntity;
import com.wkedong.springcloud.serviceproducer.mapper.UserMapper;
import com.wkedong.springcloud.serviceproducer.service.ProducerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * @author wkedong
 * <p>
 * 2019/1/14
 */
@Service
public class ProducerServiceImpl implements ProducerService {
    private Logger logger = LoggerFactory.getLogger(ProducerServiceImpl.class);

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private EurekaInstanceConfig eurekaInstanceConfig;


    @Value("${server.port}")
    private int serverPort = 0;

    @Value("${name}")
    private String configName = "";

    private String returnMessage = "";

    @Override
    public String testGet() {
        this.logger.info("/testGet, instanceId:{}, host:{}", eurekaInstanceConfig.getInstanceId(), eurekaInstanceConfig.getHostName(false));

        //1、使用JSONObject
        UserEntity userEntity = userMapper.getOne(1);
        String userEntityStr = JSONObject.toJSONString(userEntity);
        //2、使用JSONArray
        //List<UserEntity> users = userMapper.getAll();
        //String usersStr = JSONArray.toJSONString(users);
        setReturnMessage(" Get info By Mybatis is " + userEntityStr);
        return returnMessage;
    }

    @Override
    public String testPost(@RequestBody JSONObject jsonRequest) {
        this.logger.info("/testPost, instanceId:{}, host:{}", eurekaInstanceConfig.getInstanceId(), eurekaInstanceConfig.getHostName(false));
        setReturnMessage(" PostParam is " + jsonRequest.toString());
        return returnMessage;
    }

    @Override
    public String testConfig() {
        this.logger.info("/testConfig, instanceId:{}, host:{}", eurekaInstanceConfig.getInstanceId(), eurekaInstanceConfig.getHostName(false));
        setReturnMessage(" ConfigName is " + configName);
        return returnMessage;
    }

    @Override
    public String testRibbon() {
        this.logger.info("/testRibbon, instanceId:{}, host:{}", eurekaInstanceConfig.getInstanceId(), eurekaInstanceConfig.getHostName(false));
        setReturnMessage(" This is a testRibbon result");
        return returnMessage;
    }

    @Override
    public String testFeign() {
        this.logger.info("/testFeign, instanceId:{}, host:{}", eurekaInstanceConfig.getInstanceId(), eurekaInstanceConfig.getHostName(false));
        setReturnMessage(" This is a testFeign result");
        return returnMessage;
    }

    @Override
    public String testHystrix() {
        this.logger.info("/testHystrix, instanceId:{}, host:{}", eurekaInstanceConfig.getInstanceId(), eurekaInstanceConfig.getHostName(false));
        try {
            Thread.sleep(5000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        setReturnMessage(" This is a testHystrix result");
        return returnMessage;
    }

    private void setReturnMessage(String info) {
        returnMessage = "Hello, Spring Cloud! My port is " + String.valueOf(serverPort) + info;
    }

}
