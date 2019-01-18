package com.wkedong.springcloud.serviceconsumer.ribbon.hystrix.service;

/**
 * @author wkedong
 * <p>
 * 2019/1/15
 */
public interface ConsumerService {
    String consumer(String name);
}
