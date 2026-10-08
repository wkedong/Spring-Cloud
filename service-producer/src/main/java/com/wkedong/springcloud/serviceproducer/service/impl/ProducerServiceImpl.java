package com.wkedong.springcloud.serviceproducer.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.netflix.appinfo.EurekaInstanceConfig;
import com.wkedong.springcloud.serviceproducer.config.RefreshableConfig;
import com.wkedong.springcloud.serviceproducer.service.ProducerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author wkedong
 * <p>
 * 2019/1/14
 */
@Service
public class ProducerServiceImpl implements ProducerService {
    private Logger logger = LoggerFactory.getLogger(ProducerServiceImpl.class);

    @Autowired
    private EurekaInstanceConfig eurekaInstanceConfig;

    /** 配置刷新对照用：@RefreshScope 的 bean 会随 /actuator/refresh 重建 */
    @Autowired
    private RefreshableConfig refreshableConfig;

    /** Micrometer Tracing 的 Tracer（Sleuth 退役后的官方替代）：用于手动创建 span 与 Baggage */
    @Autowired
    private Tracer tracer;

    @Value("${server.port}")
    private int serverPort = 0;

    // ${name} 来自配置中心（PROPERTIES 表）；给默认值便于脱离配置中心也能完成上下文加载测试
    @Value("${name:unknown}")
    private String configName = "";

    /** /testRetry 的调用计数：第一次失败、之后成功（用于验证 LB 重试确实又发了一次请求） */
    private final AtomicInteger retryCounter = new AtomicInteger();

    private String returnMessage = "";

    @Override
    public String testGet() {
        this.logger.info("/testGet, instanceId:{}, host:{}", eurekaInstanceConfig.getInstanceId(), eurekaInstanceConfig.getHostName(false));
        setReturnMessage(" Get info is testGet Success");
        return returnMessage;
    }

    @Override
    public String testPost(JSONObject jsonRequest) {
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

    @Override
    public Map<String, Object> echoHeaders(Map<String, String> headers) {
        this.logger.info("/echoHeaders, instanceId:{}, 收到 {} 个请求头", eurekaInstanceConfig.getInstanceId(), headers.size());
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("port", serverPort);
        data.put("instanceId", eurekaInstanceConfig.getInstanceId());
        data.put("headers", headers);
        // 只挑教学相关的头单独列出，便于 curl 一眼看到关键证据
        Map<String, Object> highlight = new LinkedHashMap<String, Object>();
        highlight.put("X-From（Feign RequestInterceptor 注入）", headers.get("x-from"));
        highlight.put("X-Request-Id（业务自定义）", headers.get("x-request-id"));
        highlight.put("X-B3-TraceId（Sleuth 透传）", headers.get("x-b3-traceid"));
        highlight.put("X-B3-SpanId（Sleuth 透传）", headers.get("x-b3-spanid"));
        data.put("highlight", highlight);
        return data;
    }

    @Override
    public Map<String, Object> echoInstance() {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("port", serverPort);
        data.put("instanceId", eurekaInstanceConfig.getInstanceId());
        data.put("host", eurekaInstanceConfig.getHostName(false));
        Map<String, String> metadata = eurekaInstanceConfig.getMetadataMap();
        data.put("metadata", metadata == null ? new LinkedHashMap<String, String>() : metadata);
        data.put("version", metadata == null ? null : metadata.get("version"));
        return data;
    }

    @Override
    public String testSlow(int seconds) {
        this.logger.info("/testSlow 开始，预计 {} 秒（instanceId:{}, port:{}）", seconds, eurekaInstanceConfig.getInstanceId(), serverPort);
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        setReturnMessage(" testSlow finished after " + seconds + "s");
        return returnMessage;
    }

    @Override
    public Map<String, Object> testRetry() {
        int attempt = retryCounter.incrementAndGet();
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("port", serverPort);
        data.put("attempt", attempt);
        // 每个实例的第一次调用返回失败，之后成功 —— 这样「重试是否真的发生」可以用 count 与日志证明
        data.put("success", attempt > 1);
        this.logger.info("/testRetry 第 {} 次调用（port: {}, success: {}）", attempt, serverPort, attempt > 1);
        return data;
    }

    @Override
    public Map<String, Object> configRefreshDemo() {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("refreshScopeName", refreshableConfig.getName());
        data.put("plainValueName", configName);
        data.put("scope", refreshableConfig.getScopeInfo());
        data.put("hint", "改配置中心的 name 值后调用 POST /actuator/refresh：refreshScopeName 会变，plainValueName 不变");
        return data;
    }

    @Override
    public String spanDemo(String tag) {
        // 编程式创建自定义 span：比注解更灵活（可在任意代码位置、能动态命名与打标签）
        Span span = tracer.nextSpan().name("producer-custom-span").tag("demo.tag", tag).start();
        try (Tracer.SpanInScope scope = tracer.withSpan(span)) {
            // Baggage：随调用链传给下游的键值对（需在 management.tracing.baggage.remote-fields 中声明才会透传）
            // Micrometer Tracing 的 API 是 tracer.createBaggageInScope(...)，返回 BaggageInScope（close 后失效）
            BaggageInScope baggage = tracer.createBaggageInScope("gray-version", tag);
            try {
                Thread.sleep(120L);
                logger.info("自定义 span 内：tag={}, traceId={}, spanId={}", tag,
                        span.context().traceId(), span.context().spanId());
                setReturnMessage(" custom span done, tag=" + tag);
                return returnMessage;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "interrupted";
            } finally {
                baggage.close();
            }
        } finally {
            span.end();
        }
    }

    private void setReturnMessage(String info) {
        returnMessage = "Hello, Spring Cloud! My port is " + String.valueOf(serverPort) + info;
    }

}
