package com.wkedong.springcloud.serviceconsumer.feign.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link FeignService} 的降级工厂。
 * <p>
 * 它和 {@link ProducerFallbackFactory} 构成一组**对照实验**（两个客户端打同一个下游 500 接口，
 * 只有 FeignService 配了自定义 ErrorDecoder）：
 * <pre>
 * FeignService（有自定义 ErrorDecoder + 降级工厂） → cause = DownstreamServiceException
 * ProducerFallbackClient（默认 ErrorDecoder + 降级工厂） → cause = InternalServerError（FeignException）
 * </pre>
 * 这说明「异常类型」是 ErrorDecoder 决定的，而「是否降级」是 CircuitBreaker 决定的——
 * 两件事不要混为一谈。
 *
 * @author wkedong
 */
@Component
public class FeignServiceFallbackFactory implements FallbackFactory<FeignService> {

    private static final Logger logger = LoggerFactory.getLogger(FeignServiceFallbackFactory.class);

    @Override
    public FeignService create(Throwable cause) {
        logger.warn("FeignService 降级触发：cause={}", cause == null ? "unknown" : cause.toString());
        return new FeignService() {
            @Override
            public String testFeign() {
                return degraded("testFeign");
            }

            @Override
            public String testFeignFile(MultipartFile file) {
                return degraded("testFile(文件名=" + (file == null ? "null" : file.getOriginalFilename()) + ")");
            }

            @Override
            public Map<String, Object> echoHeaders() {
                Map<String, Object> data = new LinkedHashMap<String, Object>();
                data.put("degraded", true);
                data.put("cause", brief(cause));
                return data;
            }

            @Override
            public String testSlow(int seconds) {
                return degraded("testSlow?seconds=" + seconds);
            }

            @Override
            public String testError() {
                return degraded("testError");
            }

            private String degraded(String method) {
                return "【降级】" + method + " 调用失败，原因类型=" + brief(cause) + "（FeignService fallbackFactory 兜底）";
            }
        };
    }

    private static String brief(Throwable cause) {
        return cause == null ? "unknown" : cause.getClass().getSimpleName();
    }
}
