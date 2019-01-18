package com.wkedong.springcloud.serviceproducer.service.impl;

import com.alibaba.fastjson.JSONArray;
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

import java.util.List;

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

    @Override
    public String producer(@RequestBody JSONObject jsonRequest) throws InterruptedException {
        Thread.sleep(5000L);
        UserEntity userEntity = userMapper.getOne(1);
        this.logger.info("/provide, instanceId:{}, host:{}", eurekaInstanceConfig.getInstanceId(), eurekaInstanceConfig.getHostName(false));
        return "Hello, Spring Cloud! My port is " + String.valueOf(serverPort) + " Name is " + jsonRequest.toString()
                + "My name is " + userEntity.getName() + " age is " + userEntity.getBirthday() + " area is " + userEntity.getAddress();
    }

    @Override
    public String test() {
        List<UserEntity> users = userMapper.getAll();
        UserEntity userEntity = userMapper.getOne(1);
        //2、使用JSONArray
        String usersStr = JSONArray.toJSONString(users);
        //1、使用JSONObject
        String userEntityStr = JSONObject.toJSONString(userEntity);
        return usersStr + "          " + userEntityStr;
    }
}
