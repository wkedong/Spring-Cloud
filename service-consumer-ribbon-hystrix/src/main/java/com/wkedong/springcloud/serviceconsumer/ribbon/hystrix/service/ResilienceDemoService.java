package com.wkedong.springcloud.serviceconsumer.ribbon.hystrix.service;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Resilience4j 全家桶演示（替代 Hystrix 之后的完整弹性能力）。
 * <p>
 * 六个模块的分工，先记住一句话：<b>它们解决的是不同问题，不是同一件事的不同写法</b>。
 * <table border="1">
 *   <tr><th>模块</th><th>解决的问题</th><th>典型配置</th></tr>
 *   <tr><td>Retry</td><td>偶发失败（网络抖动）自动再试</td><td>max-attempts、wait-duration、退避</td></tr>
 *   <tr><td>CircuitBreaker</td><td>下游持续故障时快速失败，避免拖垮自己</td><td>failure-rate-threshold、wait-duration-in-open-state</td></tr>
 *   <tr><td>Bulkhead</td><td>限制并发数，防止一个慢依赖占满线程</td><td>max-concurrent-calls、max-wait-duration</td></tr>
 *   <tr><td>RateLimiter</td><td>限制「单位时间调用次数」，保护下游配额</td><td>limit-for-period、limit-refresh-period</td></tr>
 *   <tr><td>TimeLimiter</td><td>给异步调用设超时上限</td><td>timeout-duration、cancel-running-future</td></tr>
 *   <tr><td>Decorators</td><td>把上面多个能力**按顺序**串起来</td><td>组合顺序有语义，见 {@link #decoratorsDemo()}</td></tr>
 * </table>
 * 观察入口：{@code /actuator/retries}、{@code /actuator/bulkheads}、{@code /actuator/ratelimiters}、
 * {@code /actuator/timelimiters}、{@code /actuator/circuitbreakers}（以及 *events 事件端点）。
 *
 * @author wkedong
 */
@Service
public class ResilienceDemoService {

    private static final Logger logger = LoggerFactory.getLogger(ResilienceDemoService.class);

    @Autowired
    private RetryRegistry retryRegistry;

    @Autowired
    private BulkheadRegistry bulkheadRegistry;

    @Autowired
    private RateLimiterRegistry rateLimiterRegistry;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    /** Spring Cloud CircuitBreaker 抽象（内部即 Resilience4j + TimeLimiter），用于超时降级 */
    @Autowired
    private CircuitBreakerFactory circuitBreakerFactory;

    @Autowired
    private RestTemplate restTemplate;

    /** 演示用线程池：仅用于 Bulkhead 的并发压测，生产不要这么随手创建 */
    private final ExecutorService demoPool = Executors.newFixedThreadPool(5);

    @PreDestroy
    public void shutdown() {
        demoPool.shutdownNow();
    }

    /**
     * Retry 演示：业务代码连续失败 2 次、第 3 次成功。
     * <p>
     * 期望结果：{@code attempts=3}、{@code success=true} —— 说明重试把偶发失败「抹平」了。
     * 注意：重试只适合**幂等**操作；写操作重试必须要有幂等键。
     */
    public Map<String, Object> retryDemo(int succeedAtAttempt) {
        Retry retry = retryRegistry.retry("producerRetry");
        final AtomicInteger attempt = new AtomicInteger();
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        try {
            String result = Retry.decorateSupplier(retry, (Supplier<String>) () -> {
                int current = attempt.incrementAndGet();
                logger.info("retryDemo 第 {} 次尝试（目标第 {} 次成功）", current, succeedAtAttempt);
                if (current < succeedAtAttempt) {
                    throw new IllegalStateException("模拟第 " + current + " 次调用失败");
                }
                return "第 " + current + " 次调用成功";
            }).get();
            data.put("success", true);
            data.put("result", result);
        } catch (Exception e) {
            data.put("success", false);
            data.put("exceptionType", e.getClass().getSimpleName());
            data.put("message", e.getMessage());
        }
        data.put("attempts", attempt.get());
        data.put("metrics", retryMetrics(retry));
        return data;
    }

    /**
     * Bulkhead 演示：并发 5 个任务，但 bulkhead 限制同时只允许 2 个（max-wait-duration=0，等不到就拒绝）。
     * <p>
     * 期望结果：2 个 permitted、3 个 BulkheadFullException。
     * 与 RateLimiter 的区别：Bulkhead 限的是**同时进行**的数量（并发度），RateLimiter 限的是**单位时间**的次数。
     */
    public Map<String, Object> bulkheadDemo(int concurrency) {
        final Bulkhead bulkhead = bulkheadRegistry.bulkhead("producerBulkhead");
        List<Future<Map<String, Object>>> futures = new ArrayList<Future<Map<String, Object>>>();
        for (int i = 0; i < concurrency; i++) {
            final int index = i;
            futures.add(demoPool.submit((Callable<Map<String, Object>>) () -> {
                Map<String, Object> item = new LinkedHashMap<String, Object>();
                item.put("call", index);
                long start = System.currentTimeMillis();
                try {
                    String result = Bulkhead.decorateSupplier(bulkhead, () -> {
                        sleep(800L);
                        return "处理完成";
                    }).get();
                    item.put("permitted", true);
                    item.put("result", result);
                } catch (Exception e) {
                    item.put("permitted", false);
                    item.put("exceptionType", e.getClass().getSimpleName());
                }
                item.put("costMillis", System.currentTimeMillis() - start);
                return item;
            }));
        }
        List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
        int permitted = 0;
        int rejected = 0;
        for (Future<Map<String, Object>> future : futures) {
            try {
                Map<String, Object> item = future.get();
                results.add(item);
                if (Boolean.TRUE.equals(item.get("permitted"))) {
                    permitted++;
                } else {
                    rejected++;
                }
            } catch (Exception e) {
                logger.warn("收集 bulkhead 结果失败", e);
            }
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("concurrency", concurrency);
        data.put("permitted", permitted);
        data.put("rejected", rejected);
        data.put("name", bulkhead.getName());
        data.put("maxConcurrentCalls", bulkhead.getBulkheadConfig().getMaxConcurrentCalls());
        data.put("availableConcurrentCalls", bulkhead.getMetrics().getAvailableConcurrentCalls());
        data.put("results", results);
        return data;
    }

    /**
     * RateLimiter 演示：配额 3 次 / 10 秒。
     * <p>
     * 连续调用 5 次（10 秒内）即可看到前 3 次 {@code permitted=true}、后 2 次 RequestNotPermitted。
     * 生产用途：保护「有配额的第三方接口」（如短信、支付），或对单用户做频次控制。
     */
    public Map<String, Object> rateLimiterDemo() {
        RateLimiter limiter = rateLimiterRegistry.rateLimiter("producerRateLimiter");
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        long start = System.currentTimeMillis();
        try {
            String result = RateLimiter.decorateSupplier(limiter, () -> "调用被放行").get();
            data.put("permitted", true);
            data.put("result", result);
        } catch (Exception e) {
            data.put("permitted", false);
            data.put("exceptionType", e.getClass().getSimpleName());
            data.put("message", "限流器拒绝：配额已用尽，请稍后重试");
        }
        data.put("costMillis", System.currentTimeMillis() - start);
        data.put("limitForPeriod", limiter.getRateLimiterConfig().getLimitForPeriod());
        data.put("limitRefreshPeriod", limiter.getRateLimiterConfig().getLimitRefreshPeriod().toString());
        data.put("availablePermissions", limiter.getMetrics().getAvailablePermissions());
        return data;
    }

    /**
     * TimeLimiter 演示：调用 producer 的慢接口（3 秒），但超时上限 1 秒。
     * <p>
     * 这里走 Spring Cloud CircuitBreaker 抽象（{@code circuitBreakerFactory.create("slowCall")}），
     * 它的实现里已经集成了 TimeLimiter：命名的 {@code slowCall} 在
     * {@code resilience4j.timelimiter.instances.slowCall.timeout-duration} 配了 1s。
     * 期望：1 秒后走 fallback，而不是等满 3 秒。
     */
    public Map<String, Object> timeLimiterDemo(int seconds) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        long start = System.currentTimeMillis();
        String result = circuitBreakerFactory.create("slowCall").run(
                () -> restTemplate.getForObject("http://service-producer/testSlow?seconds=" + seconds, String.class),
                throwable -> {
                    logger.warn("TimeLimiter 触发降级：{}", throwable.toString());
                    return "【降级】producer 响应超过 1s 上限（" + throwable.getClass().getSimpleName() + "）";
                });
        data.put("seconds", seconds);
        data.put("result", result);
        data.put("costMillis", System.currentTimeMillis() - start);
        data.put("hint", "costMillis 应接近 1000 而不是 " + seconds * 1000);
        return data;
    }

    /**
     * Decorators 组合演示：把 Retry、CircuitBreaker、Bulkhead、Fallback 串成一条链。
     * <p>
     * <b>顺序就是语义</b>：{@code ofSupplier(业务).withRetry().withCircuitBreaker().withBulkhead().withFallback()}，
     * 越靠前越「内层」。所以这里的行为是：
     * Bulkhead 先决定要不要放进来 → CircuitBreaker 判断是否短路 → Retry 在内部重复执行 → 全部失败才走 Fallback。
     * <p>
     * 常见错误：
     * <ul>
     *   <li>把 Retry 放在 CircuitBreaker 外面：重试会把失败次数放大进统计，可能更快打开熔断；</li>
     *   <li>忘记 withFallback：异常会直接抛给调用方，等于没用上弹性能力；</li>
     *   <li>Fallback 返回「成功」语义：把故障伪装成正常，是最危险的降级方式。</li>
     * </ul>
     *
     * @param fail true 时强制业务失败，用于观察重试与兜底
     */
    public Map<String, Object> decoratorsDemo(boolean fail) {
        Retry retry = retryRegistry.retry("decoratedRetry");
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("decoratedDemo");
        Bulkhead bulkhead = bulkheadRegistry.bulkhead("decoratedBulkhead");
        final AtomicInteger attempt = new AtomicInteger();

        Supplier<String> decorated = Decorators.ofSupplier((Supplier<String>) () -> {
            int current = attempt.incrementAndGet();
            if (fail) {
                throw new IllegalStateException("模拟业务失败（第 " + current + " 次）");
            }
            return "第 " + current + " 次调用成功";
        })
                .withRetry(retry)
                .withCircuitBreaker(circuitBreaker)
                .withBulkhead(bulkhead)
                .withFallback(throwable -> "【兜底】" + throwable.getClass().getSimpleName() + "：" + throwable.getMessage())
                .decorate();

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("fail", fail);
        data.put("result", decorated.get());
        data.put("attempts", attempt.get());
        data.put("circuitBreakerState", circuitBreaker.getState().name());
        return data;
    }

    /** 各弹性组件的实时状态（等价于 Hystrix Dashboard 想做的事，但更细） */
    public Map<String, Object> status() {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        Retry retry = retryRegistry.retry("producerRetry");
        data.put("retry.producerRetry", retryMetrics(retry));
        Bulkhead bulkhead = bulkheadRegistry.bulkhead("producerBulkhead");
        Map<String, Object> bulkheadMetrics = new LinkedHashMap<String, Object>();
        bulkheadMetrics.put("maxConcurrentCalls", bulkhead.getBulkheadConfig().getMaxConcurrentCalls());
        bulkheadMetrics.put("availableConcurrentCalls", bulkhead.getMetrics().getAvailableConcurrentCalls());
        bulkheadMetrics.put("maxWaitDuration", bulkhead.getBulkheadConfig().getMaxWaitDuration().toString());
        data.put("bulkhead.producerBulkhead", bulkheadMetrics);
        RateLimiter limiter = rateLimiterRegistry.rateLimiter("producerRateLimiter");
        Map<String, Object> limiterMetrics = new LinkedHashMap<String, Object>();
        limiterMetrics.put("availablePermissions", limiter.getMetrics().getAvailablePermissions());
        limiterMetrics.put("limitForPeriod", limiter.getRateLimiterConfig().getLimitForPeriod());
        limiterMetrics.put("numberOfWaitingThreads", limiter.getMetrics().getNumberOfWaitingThreads());
        data.put("ratelimiter.producerRateLimiter", limiterMetrics);
        Map<String, Object> breakers = new LinkedHashMap<String, Object>();
        for (CircuitBreaker breaker : circuitBreakerRegistry.getAllCircuitBreakers()) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("state", breaker.getState().name());
            item.put("failureRate", breaker.getMetrics().getFailureRate());
            item.put("numberOfBufferedCalls", breaker.getMetrics().getNumberOfBufferedCalls());
            item.put("numberOfFailedCalls", breaker.getMetrics().getNumberOfFailedCalls());
            breakers.put(breaker.getName(), item);
        }
        data.put("circuitBreakers", breakers);
        return data;
    }

    private Map<String, Object> retryMetrics(Retry retry) {
        Map<String, Object> metrics = new LinkedHashMap<String, Object>();
        metrics.put("successfulCallsWithRetry", retry.getMetrics().getNumberOfSuccessfulCallsWithRetryAttempt());
        metrics.put("successfulCallsWithoutRetry", retry.getMetrics().getNumberOfSuccessfulCallsWithoutRetryAttempt());
        metrics.put("failedCallsWithRetry", retry.getMetrics().getNumberOfFailedCallsWithRetryAttempt());
        metrics.put("failedCallsWithoutRetry", retry.getMetrics().getNumberOfFailedCallsWithoutRetryAttempt());
        metrics.put("maxAttempts", retry.getRetryConfig().getMaxAttempts());
        return metrics;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
