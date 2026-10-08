package com.wkedong.springcloud.sentinel.service;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.wkedong.springcloud.sentinel.web.ApiResponse;
import com.wkedong.springcloud.sentinel.web.DemoLimits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 注解式流控演示：{@code @SentinelResource} 的 blockHandler 与 fallback。
 * <p>
 * 这是 Spring Cloud Alibaba 里最容易混淆的一点，用一句话记住：
 * <pre>
 * blockHandler：被「规则」拦住时执行 —— 流控 / 熔断降级 / 系统保护 / 热点参数，拦人的是 Sentinel 自己；
 * fallback    ：被「业务异常」打断时执行 —— 方法内部自己抛的异常（含 Feign 调用失败抛出的异常）。
 * </pre>
 * 两者都写、方法签名匹配时，Sentinel 按「谁的原因谁处理」路由：
 * 规则拦截 → blockHandler；业务异常 → fallback。所以它们是互补关系，不是二选一。
 * <p>
 * 另一个高频疑问「没配 blockHandler 时，被拦的异常为什么不是 7001 而是 500」：
 * blockHandler 缺省时 Sentinel 会把 {@code BlockException} 直接抛给调用方。
 * {@code BlockException} 是**受检异常**（{@code extends Exception}），
 * 它从 Controller 方法里逃出去会被 Spring MVC 包装成 {@code ServletException} +
 * {@code NestedServletException}，最终变成 HTTP 500 的默认错误页——
 * 而不是你期望的「友好限流提示」。所以「配了 @SentinelResource 就万事大吉」是错的：
 * <b>不写 blockHandler，限流就会以 500 的形式暴露给调用方</b>。
 * 本模块的 {@code GlobalExceptionHandler} 专门给 BlockException 加了一个 429 分支，
 * 讲的就是「不做这层兜底会怎样」。
 * <p>
 * 计数说明：这里的 {@link #BLOCKED} / {@link #FALLBACK} 只是为了在 /sentinel/status 里
 * 用一眼能懂的数字展示「谁被谁拦了」；完整的统计值直接读 Sentinel 的 ClusterNode。
 *
 * @author wkedong
 */
@Service
public class FlowDemoService {

    private static final Logger log = LoggerFactory.getLogger(FlowDemoService.class);

    /** 被规则拦截的累计次数（blockHandler 触发次数） */
    private static final AtomicInteger BLOCKED = new AtomicInteger();
    /** 业务异常走兜底的累计次数（fallback 触发次数） */
    private static final AtomicInteger FALLBACK = new AtomicInteger();
    /** 业务异常演示接口的调用序号：偶数次成功、奇数次抛异常 */
    private static final AtomicInteger EXCEPTION_CALLS = new AtomicInteger();
    /** 「只抛一次」演示接口的调用序号 */
    private static final AtomicInteger FALLBACK_DEMO_CALLS = new AtomicInteger();

    /**
     * QPS 快速失败（资源 flow-qps，阈值 2）。
     * <p>
     * 为什么要有 blockHandler：限流是「预料之中的拒绝」，不该伪装成系统故障（500），
     * 而应该返回「当前太忙，稍后再试」的友好结构——这就是 blockHandler 的职责。
     */
    public ApiResponse<Map<String, Object>> qps(String note) {
        return ApiResponse.ok(payload("flow-qps", "QPS 快速失败：阈值 2，秒内第 3 个起被拦", note));
    }

    /**
     * flow-qps 的 blockHandler：参数与原方法一致，末尾多一个 {@link BlockException}。
     * <p>
     * 签名规则（写错是最常见的「blockHandler 不生效」原因）：
     * 返回值类型一致、参数列表一致（顺序不可变）、末尾加 BlockException、必须是 public，
     * 且默认必须与原方法在同一个类中。
     */
    public ApiResponse<Map<String, Object>> qpsBlockHandler(String note, BlockException ex) {
        BLOCKED.incrementAndGet();
        log.warn("=== flow-qps 被规则拦截：blockException={}, rule={} ===",
                ex.getClass().getSimpleName(), ex.getRule().getResource());
        Map<String, Object> data = payload("flow-qps", "blockHandler 触发：被流控规则拦下，业务方法根本没执行", note);
        data.put("blockException", ex.getClass().getSimpleName());
        data.put("rule", ex.getRule().getResource());
        ApiResponse<Map<String, Object>> response = ApiResponse.fail(7001, "被 Sentinel 规则拦截（flow limiting）");
        response.setData(data);
        return response;
    }

    /**
     * 并发线程数模式（资源 flow-concurrent，阈值 2 个线程）。
     * <p>
     * 为什么除了 QPS 还要看线程数：QPS 低不代表没风险。若每次调用耗时 1 秒，
     * 2 QPS 就意味着常年有 2 个线程被占住；下游再慢一点，线程池会被抽干，故障照样扩散。
     * 线程数模式统计的是「同一瞬间在方法内的线程数」，与 QPS 是两个维度。
     */
    public ApiResponse<Map<String, Object>> concurrent(long sleepMs) {
        sleep(sleepMs);
        return ApiResponse.ok(payload("flow-concurrent", "并发线程数模式：同一瞬间最多 2 个线程", "sleepMs=" + sleepMs));
    }

    /** flow-concurrent 的 blockHandler */
    public ApiResponse<Map<String, Object>> concurrentBlockHandler(long sleepMs, BlockException ex) {
        BLOCKED.incrementAndGet();
        Map<String, Object> data = payload("flow-concurrent", "blockHandler 触发：同时进入的线程数超过 2", "sleepMs=" + sleepMs);
        data.put("blockException", ex.getClass().getSimpleName());
        ApiResponse<Map<String, Object>> response = ApiResponse.fail(7001, "被 Sentinel 规则拦截（flow limiting）");
        response.setData(data);
        return response;
    }

    /**
     * 排队等待（资源 flow-queue，QPS=2 + maxQueueingTimeMs=2000）。
     * <p>
     * 与快速失败的区别：多出来的请求不会立刻被拒，而是被「匀速放行」——
     * 代价是响应时间被拉长，好处是不丢请求，适合异步任务/削峰填谷。
     */
    public ApiResponse<Map<String, Object>> queue() {
        return ApiResponse.ok(payload("flow-queue", "排队等待：QPS=2，超出部分匀速放行（最多等 2s）", "controlBehavior=RATE_LIMITER"));
    }

    /** flow-queue 的 blockHandler：只有等超过 maxQueueingTimeMs 才会走到这里 */
    public ApiResponse<Map<String, Object>> queueBlockHandler(BlockException ex) {
        BLOCKED.incrementAndGet();
        Map<String, Object> data = payload("flow-queue", "blockHandler 触发：排队超过 maxQueueingTimeMs=2000ms，放弃等待", "controlBehavior=RATE_LIMITER");
        data.put("blockException", ex.getClass().getSimpleName());
        ApiResponse<Map<String, Object>> response = ApiResponse.fail(7001, "被 Sentinel 规则拦截（flow limiting）");
        response.setData(data);
        return response;
    }

    /**
     * 演示「业务异常走 fallback」：同一个接口偶数次调用成功、奇数次调用抛异常。
     * <p>
     * 这样一条命令（连打 4 次）就能看到：fallback 生效、而 blockHandler 一次都没触发——
     * 因为异常来自业务方法内部，Sentinel 规则并没有拦它。
     */
    public ApiResponse<Map<String, Object>> businessException(String note) {
        int seq = EXCEPTION_CALLS.incrementAndGet();
        if (seq % 2 == 1) {
            // 模拟业务校验失败 / 下游返回错误码：这是「业务异常」，不是规则拦截
            throw new IllegalStateException("模拟业务异常：第 " + seq + " 次调用注定失败");
        }
        Map<String, Object> data = payload("flow-qps", "第 " + seq + " 次调用成功（偶数次成功、奇数次异常）", note);
        data.put("seq", seq);
        return ApiResponse.ok(data);
    }

    /**
     * businessException 的 fallback：捕获业务方法抛出的任意异常（含 Feign 调用失败抛出的异常）。
     * <p>
     * 注意：如果同时配置了 blockHandler，被规则拦截时就轮不到 fallback；
     * 只有在「没有 blockHandler」或「异常不是 BlockException」时才走这里。
     * 这里显式把 BlockException 转成 RuntimeException 抛回去，保证「规则拦截」与「业务异常」
     * 两条路泾渭分明：被规则拦住就该由 blockHandler 处理，而不是被 fallback 悄悄吞掉。
     * <p>
     * 为什么用 {@code toRuntimeException()}：{@code BlockException} 是**受检异常**
     * （{@code extends Exception}，不是 RuntimeException），在 fallback 方法里不能直接
     * {@code throw (BlockException) t}——编译器会要求声明 throws，而 Sentinel 规定的
     * fallback/blockHandler 签名里没有 throws。Sentinel 自己提供了这个转换方法。
     */
    public ApiResponse<Map<String, Object>> businessExceptionFallback(String note, Throwable t) {
        if (t instanceof BlockException) {
            throw ((BlockException) t).toRuntimeException();
        }
        FALLBACK.incrementAndGet();
        log.warn("=== flow-qps 业务异常走 fallback：{} ===", t.getMessage());
        Map<String, Object> data = payload("flow-qps", "fallback 触发：业务方法自己抛了异常，不是规则拦截", note);
        data.put("exception", t.getClass().getSimpleName());
        data.put("exceptionMessage", t.getMessage());
        ApiResponse<Map<String, Object>> response = ApiResponse.fail(7002, "业务异常已降级兜底（fallback）");
        response.setData(data);
        return response;
    }

    /**
     * 稳定复现 fallback：第一次调用必抛业务异常（走 fallback），之后都成功。
     * <p>
     * 为什么要单独做一个「只抛一次」的接口：{@code /demo/exception} 会持续抛异常，
     * 一旦异常把降级规则统计堆满，后面的请求就会变成「被熔断拦下」——
     * 那样输出的就变成 blockHandler 了，反而说不清 fallback 本身。
     * 这个接口只抛一次，保证输出里<b>必然</b>是「fallback 触发 + blockHandler 不触发」。
     */
    public ApiResponse<Map<String, Object>> alwaysThrowFirst(String note) {
        int seq = FALLBACK_DEMO_CALLS.incrementAndGet();
        if (seq == 1) {
            throw new IllegalStateException("模拟业务异常：下游返回错误码/参数校验失败（只抛一次，保证能稳定复现 fallback）");
        }
        Map<String, Object> data = payload("flow-qps", "第 " + seq + " 次调用成功（只有第 1 次会抛异常）", note);
        data.put("seq", seq);
        return ApiResponse.ok(data);
    }

    /** 重置计数器，方便反复做实验（不影响 Sentinel 自己维护的统计窗口） */
    public Map<String, Object> resetCounters() {
        BLOCKED.set(0);
        FALLBACK.set(0);
        EXCEPTION_CALLS.set(0);
        FALLBACK_DEMO_CALLS.set(0);
        return counters();
    }

    /** 供 /sentinel/status 使用：把 blockHandler / fallback 的触发次数曝光出来 */
    public Map<String, Object> counters() {
        Map<String, Object> counters = new LinkedHashMap<>();
        counters.put("blockHandler 触发次数", BLOCKED.get());
        counters.put("fallback 触发次数", FALLBACK.get());
        counters.put("flow-qps 调用序号", EXCEPTION_CALLS.get());
        return counters;
    }

    /** 统一构造返回数据，减少各演示方法里的重复代码 */
    private Map<String, Object> payload(String resource, String explain, String note) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("resource", resource);
        data.put("explain", explain);
        data.put("note", note);
        data.put("thread", Thread.currentThread().getName());
        return data;
    }

    private void sleep(long millis) {
        if (millis <= 0L || millis > DemoLimits.MAX_SLEEP_MS) {
            // 双保险：注解只校验 HTTP 入口，此处再挡一次非法值（负数会让 sleep 直接抛异常）
            return;
        }
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
            // 恢复中断标志，避免把「该退出」的信号丢掉
            Thread.currentThread().interrupt();
        }
    }
}
