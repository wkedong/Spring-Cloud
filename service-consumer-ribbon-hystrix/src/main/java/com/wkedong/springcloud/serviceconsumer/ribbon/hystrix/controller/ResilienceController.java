package com.wkedong.springcloud.serviceconsumer.ribbon.hystrix.controller;

import com.wkedong.springcloud.serviceconsumer.ribbon.hystrix.service.ResilienceDemoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Resilience4j 全家桶演示端点（对应 docs/20）。
 * <p>
 * <pre>
 * /resilience/status       各组件的实时状态（熔断状态、剩余并发/配额、重试统计）
 * /resilience/retry        重试：连续失败 2 次、第 3 次成功
 * /resilience/bulkhead     并发隔离：5 个并发只有 2 个被放行
 * /resilience/ratelimiter  速率限制：连点 5 次只有 3 次被放行（配额 3 次/10 秒）
 * /resilience/timelimiter  超时：下游 3 秒，1 秒即降级
 * /resilience/decorators   组合：Retry + CircuitBreaker + Bulkhead + Fallback 的执行顺序
 * </pre>
 *
 * @author wkedong
 */
@RestController
public class ResilienceController {

    @Autowired
    private ResilienceDemoService resilienceDemoService;

    @GetMapping("/resilience/status")
    public Map<String, Object> status() {
        return resilienceDemoService.status();
    }

    @GetMapping("/resilience/retry")
    public Map<String, Object> retry(@RequestParam(value = "succeedAtAttempt", defaultValue = "3") int succeedAtAttempt) {
        return resilienceDemoService.retryDemo(succeedAtAttempt);
    }

    @GetMapping("/resilience/bulkhead")
    public Map<String, Object> bulkhead(@RequestParam(value = "concurrency", defaultValue = "5") int concurrency) {
        return resilienceDemoService.bulkheadDemo(concurrency);
    }

    @GetMapping("/resilience/ratelimiter")
    public Map<String, Object> rateLimiter() {
        return resilienceDemoService.rateLimiterDemo();
    }

    @GetMapping("/resilience/timelimiter")
    public Map<String, Object> timeLimiter(@RequestParam(value = "seconds", defaultValue = "3") int seconds) {
        return resilienceDemoService.timeLimiterDemo(seconds);
    }

    @GetMapping("/resilience/decorators")
    public Map<String, Object> decorators(@RequestParam(value = "fail", defaultValue = "false") boolean fail) {
        return resilienceDemoService.decoratorsDemo(fail);
    }
}
