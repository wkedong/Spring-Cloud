package com.wkedong.springcloud.serviceproducer.service;

import com.alibaba.fastjson.JSONObject;

/**
 * @author wkedong
 * <p>
 * 2019/1/15
 */
public interface ProducerService {
    String testGet();

    String testPost(JSONObject jsonRequest);

    String testConfig();

    String testRibbon();

    String testFeign();

    String testHystrix();
}
