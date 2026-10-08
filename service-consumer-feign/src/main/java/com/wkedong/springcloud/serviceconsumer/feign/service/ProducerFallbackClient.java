package com.wkedong.springcloud.serviceconsumer.feign.service;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 带降级工厂（FallbackFactory）的 Feign 客户端。
 * <p>
 * 与 {@link FeignService} 的区别：
 * <ul>
 *   <li>用 {@code contextId} 区分：同一个服务名可以有多个客户端接口，Spring 用 contextId 作为配置隔离键，
 *       不指定会抛 "Multiple Feign Clients with the same name..." 异常；</li>
 *   <li>用 {@code fallbackFactory} 而不是 {@code fallback}：工厂能拿到**触发降级的异常**，
 *       可以按异常类型返回不同兜底结果，也能打日志——而 fallback 类拿不到原因。</li>
 * </ul>
 * 前置条件：{@code feign.circuitbreaker.enabled=true}（否则 fallback 不会生效），
 * 且类路径需要 Spring Cloud CircuitBreaker 实现（本模块用 Resilience4j）。
 *
 * @author wkedong
 */
@FeignClient(name = "service-producer", contextId = "producerFallback",
        fallbackFactory = ProducerFallbackFactory.class)
public interface ProducerFallbackClient {

    /** 下游故意 500：验证降级工厂是否被触发，以及它能否拿到原因 */
    @GetMapping("/testError")
    String testError();

    /** 下游慢调用：验证「超时 → 降级」链路 */
    @GetMapping("/testSlow")
    String testSlow(@RequestParam("seconds") int seconds);
}
