package com.wkedong.springboot.basics.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 异步任务服务。
 * <p>
 * 教学点：
 * <ul>
 *   <li>{@code @Async} 方法必须由**另一个 bean** 调用（同类自调用同样失效，原因与事务一致——代理）；</li>
 *   <li>返回 {@link CompletableFuture} 才能让调用方编排（allOf / thenApply / 超时）；</li>
 *   <li>返回值类型只能是 void 或 Future 家族，否则拿不到异步结果；</li>
 *   <li>线程池由 {@code AsyncConfig} 提供（不指定就用默认的，生产不可接受）。</li>
 * </ul>
 *
 * @author wkedong
 */
@Service
public class AsyncTaskService {

    private static final Logger log = LoggerFactory.getLogger(AsyncTaskService.class);

    private final AtomicInteger executed = new AtomicInteger();

    /** 模拟一个耗时任务（如调用第三方接口） */
    @Async("basicsExecutor")
    public CompletableFuture<String> work(int index, long sleepMillis) {
        long start = System.currentTimeMillis();
        sleepQuietly(sleepMillis);
        executed.incrementAndGet();
        String thread = Thread.currentThread().getName();
        long cost = System.currentTimeMillis() - start;
        log.info("异步任务 #{} 完成：线程={}, 耗时={}ms", index, thread, cost);
        return CompletableFuture.completedFuture("task-" + index + " on " + thread + " (" + cost + "ms)");
    }

    /** 返回 void 的异步方法：异常不会传给调用方，只能靠 AsyncUncaughtExceptionHandler 兜住 */
    @Async("basicsExecutor")
    public void fireAndForget(boolean fail) {
        if (fail) {
            throw new IllegalStateException("演示异常：void 类型的异步方法内部抛错，调用方感知不到");
        }
        log.info("void 异步任务正常完成");
    }

    public int executedCount() {
        return executed.get();
    }

    /** 同步执行同样逻辑（用于与异步做耗时对比） */
    public Map<String, Object> runSync(int taskCount, long sleepMillis, Map<String, Object> result) {
        long start = System.currentTimeMillis();
        for (int i = 0; i < taskCount; i++) {
            sleepQuietly(sleepMillis);
        }
        result.put("syncCostMillis", System.currentTimeMillis() - start);
        return result;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
