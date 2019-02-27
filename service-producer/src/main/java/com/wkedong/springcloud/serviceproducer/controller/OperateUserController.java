package com.wkedong.springcloud.serviceproducer.controller;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.wkedong.springcloud.serviceproducer.entity.UserEntity;
import com.wkedong.springcloud.serviceproducer.service.OperateUserService;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author wkedong
 * 操作User表
 * Create in 2019/2/27 10:08
 * Update in 2019/2/27 10:08
 */
@RestController
public class OperateUserController {

    private final Logger logger = Logger.getLogger(getClass());

    @Autowired
    OperateUserService operateUserService;

    @PostMapping(value = "/getOne")
    public String getOne(@RequestBody JSONObject jsonRequest) {
        logger.info("===<call getOne>===");
        int id = NumberUtils.toInt(String.valueOf(jsonRequest.get("id")));
        return JSONObject.toJSONString(operateUserService.getOne(id));
    }

    @PostMapping(value = "/getAll")
    public String getAll() {
        logger.info("===<call getAll>===");
        return JSONArray.toJSONString(operateUserService.getAll());
    }

    @PostMapping(value = "/insert")
    public String insert(@RequestBody JSONObject jsonRequest) {
        logger.info("===<call insert>===");
        UserEntity user = JSONObject.parseObject(String.valueOf(jsonRequest), UserEntity.class);
        boolean result = operateUserService.insertData(user);
        return "Insert data result is " + JSONObject.toJSONString(result);
    }

    @PostMapping(value = "/update")
    public String update(@RequestBody JSONObject jsonRequest) {
        logger.info("===<call update>===");
        UserEntity user = JSONObject.parseObject(String.valueOf(jsonRequest), UserEntity.class);
        boolean result = operateUserService.updateData(user);
        return "Update data result is " + JSONObject.toJSONString(result);
    }

    @PostMapping(value = "/delete")
    public String delete(@RequestBody JSONObject jsonRequest) {
        logger.info("===<call delete>===");
        int id = NumberUtils.toInt(String.valueOf(jsonRequest.get("id")));
        boolean result = operateUserService.deleteData(id);
        return "Delete data result is " + JSONObject.toJSONString(result);
    }
}
