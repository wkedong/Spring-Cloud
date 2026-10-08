package com.wkedong.springcloud.serviceconsumer.feign.config;

import com.wkedong.springcloud.serviceconsumer.feign.exception.DownstreamServiceException;
import com.wkedong.springcloud.serviceconsumer.feign.exception.NotFoundException;
import com.wkedong.springcloud.serviceconsumer.feign.interceptor.BusinessHeaderInterceptor;
import feign.Logger;
import feign.RequestInterceptor;
import feign.Response;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;

import java.io.IOException;

/**
 * 某一个 Feign 客户端的专属配置（在 {@code @FeignClient(configuration = ProducerFeignConfig.class)} 上引用）。
 * <p>
 * ⚠️ 本类**故意不加 {@code @Configuration}**：
 * 它位于组件扫描路径下，如果加了 {@code @Configuration}，里面的 Bean 会被注册到父容器，
 * 从而变成**所有** Feign 客户端的默认配置（这是官方文档反复强调的坑）。
 * 只保留普通类 + {@code @Bean} 方法，由 Feign 在「该客户端的子容器」里注册。
 *
 * @author wkedong
 */
public class ProducerFeignConfig {

    /** 请求前统一加工请求头 */
    @Bean
    public RequestInterceptor businessHeaderInterceptor() {
        return new BusinessHeaderInterceptor();
    }

    /**
     * Feign 日志级别：NONE / BASIC（方法、URL、状态码、耗时）/ HEADERS / FULL（含 body）。
     * <p>
     * 注意两个前提，缺一个都看不到日志：
     * <ol>
     *   <li>这里返回的 {@link Logger.Level} 要生效；</li>
     *   <li>对应 Feign 客户端接口所在包的日志级别必须是 DEBUG：
     *       {@code logging.level.com.wkedong.springcloud.serviceconsumer.feign.service: debug}</li>
     * </ol>
     * 生产环境用 BASIC/HEADERS 即可，FULL 会把请求体打进日志（含敏感数据）。
     */
    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }

    /**
     * 自定义错误解码器：把下游的非 2xx 响应翻译成**业务语义的异常**。
     * <p>
     * 默认行为是把所有非 2xx 一律抛 {@code FeignException}，调用方只能靠 status() 判断，
     * 很容易写出「所有错误都当系统异常处理」的代码。这里做三件事：
     * 404 → NotFoundException；5xx → DownstreamServiceException（携带下游错误码与原始 body）；
     * 其它 → 交回默认实现。
     */
    @Bean
    public ErrorDecoder producerErrorDecoder() {
        final ErrorDecoder defaultDecoder = new ErrorDecoder.Default();
        final org.slf4j.Logger log = LoggerFactory.getLogger(ProducerFeignConfig.class);
        return (methodKey, response) -> {
            String body = readBody(response);
            log.warn("下游返回非 2xx：methodKey={}, status={}, body={}", methodKey, response.status(), body);
            if (response.status() == 404) {
                return new NotFoundException("下游资源不存在：" + methodKey);
            }
            if (response.status() >= 500) {
                return new DownstreamServiceException("下游服务错误(" + response.status() + ")：" + body);
            }
            return defaultDecoder.decode(methodKey, response);
        };
    }

    /**
     * 重试策略：默认是 {@link Retryer#NEVER_RETRY}（**不重试**）。
     * 这里显式声明「最多 3 次、间隔 100ms 起、指数退避上限 1s」，用于演示 Feign 层的重试。
     * <p>
     * 提醒：Feign 层重试 + LoadBalancer 层重试会叠加（3×2=6 次），
     * 只应保留一层，否则一次用户请求会被放大成多次下游调用。
     */
    @Bean
    public Retryer feignRetryer() {
        return new Retryer.Default(100L, 1000L, 3);
    }

    private static String readBody(Response response) {
        if (response.body() == null) {
            return "";
        }
        try {
            byte[] bytes = new byte[512];
            int read = response.body().asInputStream().read(bytes);
            return read > 0 ? new String(bytes, 0, read, "UTF-8") : "";
        } catch (IOException e) {
            return "(读取响应体失败)";
        }
    }
}
