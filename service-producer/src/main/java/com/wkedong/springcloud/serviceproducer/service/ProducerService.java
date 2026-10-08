package com.wkedong.springcloud.serviceproducer.service;

import com.alibaba.fastjson.JSONObject;

import java.util.Map;

/**
 * 服务提供者能力。
 * <p>
 * 教学扩展端点（echoHeaders / echoInstance / testSlow / testRetry / testConfigRefresh / spanDemo）
 * 是为配套章节服务的：Feign 进阶（docs/17）、负载均衡与灰度（docs/18）、Resilience4j（docs/20）、
 * 配置刷新与链路追踪（docs/21）。
 *
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

    /** 回显请求头：用于验证 Feign 的 RequestInterceptor 与 Sleuth 头透传 */
    Map<String, Object> echoHeaders(Map<String, String> headers);

    /** 回显本实例身份：端口 + 注册实例 id + 元数据（metadata.version 用于灰度路由验证） */
    Map<String, Object> echoInstance();

    /** 慢接口：用于演示 Feign 读超时、Sentinel 慢调用熔断 */
    String testSlow(int seconds);

    /** 首次调用返回失败、之后成功：用于演示 LoadBalancer 重试（重试后同一实例已恢复） */
    Map<String, Object> testRetry();

    /** 配置刷新对照：@RefreshScope 的值会随 /actuator/refresh 变化，普通 @Value 不会 */
    Map<String, Object> configRefreshDemo();

    /** 编程式自定义 span + Baggage（Sleuth 3.x Tracer API） */
    String spanDemo(String tag);
}
