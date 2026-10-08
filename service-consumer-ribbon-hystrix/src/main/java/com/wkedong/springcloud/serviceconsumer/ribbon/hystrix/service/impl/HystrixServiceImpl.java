package com.wkedong.springcloud.serviceconsumer.ribbon.hystrix.service.impl;

import com.wkedong.springcloud.serviceconsumer.ribbon.hystrix.service.HystrixService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * 熔断降级示例
 * <p>
 * 2021.0.x 迁移说明：原 @HystrixCommand(fallbackMethod = "fallback") 写法
 * （com.netflix.hystrix.contrib.javanica 注解）已随 Hystrix 退役，
 * 等价改为 Spring Cloud CircuitBreaker 抽象（Resilience4j 实现）：
 * <pre>
 * 旧：@HystrixCommand(fallbackMethod = "fallback")
 *     public String testHystrix() { return restTemplate.getForObject(...); }
 *
 * 新：circuitBreakerFactory.create("testHystrix")
 *         .run(() -> restTemplate.getForObject(...), throwable -> fallback(throwable));
 * </pre>
 * 超时/熔断阈值在 yml 的 resilience4j.timelimiter / resilience4j.circuitbreaker 配置。
 * <p>
 * 实测踩坑：注入时必须用抽象类型 CircuitBreakerFactory，不要注入具体类
 * Resilience4JCircuitBreakerFactory——当 Sleuth 在类路径上时，工厂会被
 * AOP 包装为返回 TraceCircuitBreaker 的代理，按具体类型注入会触发协变桥接强转：
 * TraceCircuitBreaker cannot be cast to Resilience4JCircuitBreaker。
 *
 * @author wkedong
 * Hystrix
 * 2019/1/15
 */
@Service
public class HystrixServiceImpl implements HystrixService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    CircuitBreakerFactory circuitBreakerFactory;

    @Override
    public String testHystrix() {
        //执行http请求service-producer服务的testHystrix映射地址，返回的数据为字符串类型
        //服务提供者(service-producer服务)的注册服务ID
        //testHystrix ：消费方法
        //producer 的 /testHystrix 故意睡 5s，超过 yml 里 3s 的 TimeLimiter 即走 fallback
        return circuitBreakerFactory.create("testHystrix").run(
                () -> restTemplate.getForObject("http://service-producer/testHystrix", String.class),
                this::fallback);
    }

    /**
     * 降级方法（原 Hystrix fallbackMethod，签名需携带触发降级的异常）
     */
    public String fallback(Throwable throwable) {
        logger.warn("===<call testHystrix fail fallback>===, cause: {}", throwable.getMessage());
        return "service-producer /testHystrix is error";
    }
}
