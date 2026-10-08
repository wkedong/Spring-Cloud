package com.wkedong.springcloud.serviceconsumer.ribbon.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author wkedong
 * RobbinDemo
 * 2019/1/5
 */
@RestController
public class RibbonController {
    // log4j 1.x → slf4j
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private DiscoveryClient discoveryClient;

    @GetMapping(value = "/testRibbon")
    public String testRibbon() {
        logger.info("===<call testRibbon>===");

        //执行http请求Producer服务的provide映射地址，返回的数据为字符串类型
        //PRODUCER：提供者(Producer服务)的注册服务ID
        //provide ：消费方法
        return restTemplate.getForObject("http://service-producer/testRibbon", String.class);
    }

    // ======================= 教学扩展：负载均衡与灰度（docs/18） =======================

    /**
     * 灰度路由验证：请求头 {@code X-Gray-Version} 命中 producer 实例的 metadata.version 时，
     * 请求会被固定送到那个实例；不带该头则退回轮询。
     * <pre>
     * curl -H 'X-Gray-Version: v2' http://localhost:7030/testGray   # 应固定落在 v2 实例
     * curl http://localhost:7030/testGray                            # 轮询，端口会在实例间变化
     * </pre>
     */
    @GetMapping(value = "/testGray")
    @SuppressWarnings("unchecked")
    public Map<String, Object> testGray() {
        Map<String, Object> response = restTemplate.getForObject("http://service-producer/echoInstance", Map.class);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("producer", response);
        data.put("hint", "带 X-Gray-Version: v2 时端口应稳定为 v2 实例；不带则轮询");
        return data;
    }

    /**
     * 负载均衡重试验证：producer 的 {@code /testRetry} 每个实例第一次调用返回 500、之后成功。
     * <pre>
     * curl http://localhost:7030/testLbRetry
     * </pre>
     * 配置见 bootstrap.yml 的 {@code spring.cloud.loadbalancer.retry.*}（需 spring-retry 依赖）。
     */
    @GetMapping(value = "/testLbRetry")
    @SuppressWarnings("unchecked")
    public Map<String, Object> testLbRetry() {
        logger.info("===<call testLbRetry>===");
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> response = restTemplate.getForObject("http://service-producer/testRetry", Map.class);
            data.put("success", true);
            data.put("producerResponse", response);
            data.put("hint", "producerResponse.attempt > 1 说明负载均衡层重试确实重发了一次请求");
        } catch (Exception e) {
            data.put("success", false);
            data.put("exceptionType", e.getClass().getSimpleName());
            data.put("message", e.getMessage());
        }
        data.put("costMillis", System.currentTimeMillis() - start);
        return data;
    }

    /** 查看注册中心里 producer 的实例清单与元数据（灰度标记的来源） */
    @GetMapping(value = "/lb/instances")
    public Map<String, Object> instances() {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        for (ServiceInstance instance : discoveryClient.getInstances("service-producer")) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("instanceId", instance.getInstanceId());
            item.put("host", instance.getHost());
            item.put("port", instance.getPort());
            item.put("uri", instance.getUri().toString());
            item.put("metadata", instance.getMetadata());
            list.add(item);
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("serviceId", "service-producer");
        data.put("instanceCount", list.size());
        data.put("instances", list);
        data.put("knownServices", discoveryClient.getServices());
        return data;
    }
}
