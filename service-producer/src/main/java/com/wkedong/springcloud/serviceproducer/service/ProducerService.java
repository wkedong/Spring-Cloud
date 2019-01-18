package com.wkedong.springcloud.serviceproducer.service;

import com.alibaba.fastjson.JSONObject;

/**
 * @author wkedong
 * <p>
 * 2019/1/15
 */
public interface ProducerService {
    String producer(JSONObject jsonRequest);

    String test();
}
