package com.wkedong.springcloud.serviceconsumer.feign.service;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 降级工厂：Feign 调用失败（异常、超时、熔断打开）时返回兜底实现。
 * <p>
 * 教学点：
 * <ol>
 *   <li>工厂拿得到 {@code cause}，所以能区分「超时」「下游 500」「熔断打开」并分别记日志/返回不同兜底；</li>
 *   <li>必须是 Spring Bean（{@code @Component}）——Feign 需要在容器里找到它；</li>
 *   <li>兜底结果要**语义正确**：查不到数据可以是空列表，但「下单失败」不能返回成功，
 *       否则会把故障伪装成正常，这是降级最危险的用法。</li>
 * </ol>
 *
 * @author wkedong
 */
@Component
public class ProducerFallbackFactory implements FallbackFactory<ProducerFallbackClient> {

    private static final Logger logger = LoggerFactory.getLogger(ProducerFallbackFactory.class);

    @Override
    public ProducerFallbackClient create(Throwable cause) {
        logger.warn("服务降级触发：producer 调用失败，原因类型={}, 原因={}",
                cause == null ? "unknown" : cause.getClass().getSimpleName(),
                cause == null ? "unknown" : cause.getMessage());
        return new ProducerFallbackClient() {
            @Override
            public String testError() {
                return "【降级】producer /testError 调用失败：" + brief(cause) + "（由 FallbackFactory 兜底）";
            }

            @Override
            public String testSlow(int seconds) {
                return "【降级】producer /testSlow?seconds=" + seconds + " 调用失败：" + brief(cause) + "（由 FallbackFactory 兜底）";
            }
        };
    }

    private String brief(Throwable cause) {
        return cause == null ? "unknown" : cause.getClass().getSimpleName();
    }
}
