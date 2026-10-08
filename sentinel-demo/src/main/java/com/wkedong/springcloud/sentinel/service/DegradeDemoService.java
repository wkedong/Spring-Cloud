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
 * 熔断降级演示：三种 {@code DegradeRule}（慢调用比例 / 异常比例 / 异常数）各绑一个独立资源。
 * <p>
 * 为什么要熔断而不只是限流：限流保护「自己不被压垮」，熔断保护「下游已经坏了的时候别再傻等」。
 * 下游慢请求会占住调用方的线程与连接，等待本身没有收益，快速失败反而能让系统保住吞吐。
 * <p>
 * 为什么三个规则用三个资源名：不同 grade 的语义完全不同（一个按耗时、两个按异常），
 * 挤在同一个资源上会让阈值互相干扰，实验也说不清是哪种规则打开了大开关——
 * 教学 demo 里「一个规则一个资源」是最容易讲清楚的做法。
 * <p>
 * 关键点：业务异常必须真的抛出去，Sentinel 才统计得到（异常比例/异常数都靠这个计数）；
 * 如果在方法内部 try-catch 吃掉异常，统计里 error 永远是 0，熔断永远不会打开。
 * 所以这里的写法是「抛出异常 + fallback 兜底」，既让 Sentinel 计到数，
 * 又让调用方拿到友好的降级响应。
 *
 * @author wkedong
 */
@Service
public class DegradeDemoService {

    private static final Logger log = LoggerFactory.getLogger(DegradeDemoService.class);

    /** 慢调用的耗时：远高于规则里的 500ms 慢调用判定阈值，保证每次都被判为「慢」 */
    public static final long SLOW_MILLIS = 800L;

    /** 慢调用资源的 blockHandler 触发次数（即熔断打开后快速失败的次数） */
    private static final AtomicInteger SLOW_BLOCKED = new AtomicInteger();
    /** 异常比例资源的 blockHandler 触发次数 */
    private static final AtomicInteger RATIO_BLOCKED = new AtomicInteger();
    /** 异常数资源的 blockHandler 触发次数 */
    private static final AtomicInteger COUNT_BLOCKED = new AtomicInteger();
    /** 异常比例资源的调用序号：奇数次抛异常、偶数次成功，稳定产出约 50% 的异常比例 */
    private static final AtomicInteger RATIO_CALLS = new AtomicInteger();
    /** 异常数资源的「强制失败」开关 */
    private static volatile boolean alwaysFail = true;

    /**
     * 慢调用（资源 degrade-slow，规则：慢调用比例 ≥50%，最小请求数 10，熔断 10s）。
     * <p>
     * 为什么故意睡 {@value #SLOW_MILLIS}ms：慢调用比例规则的 count=500ms 是「多慢算慢」的定义，
     * 把业务耗时稳定压在阈值之上，才能让比例可预测地冲到 100%。
     */
    public ApiResponse<Map<String, Object>> slow() {
        long begin = System.currentTimeMillis();
        sleep(SLOW_MILLIS);
        long cost = System.currentTimeMillis() - begin;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("resource", "degrade-slow");
        data.put("explain", "慢调用：真实执行业务，耗时 " + cost + "ms（规则判定阈值 500ms）");
        data.put("costMs", cost);
        return ApiResponse.ok(data);
    }

    /**
     * 慢调用资源的 blockHandler：熔断打开后被调用。
     * <p>
     * 这里刻意「什么都不做」——降级逻辑应当便宜且确定成功，绝不能再去调那个已经变慢的下游。
     */
    public ApiResponse<Map<String, Object>> slowBlockHandler(BlockException ex) {
        int blocked = SLOW_BLOCKED.incrementAndGet();
        log.warn("=== degrade-slow 熔断打开，快速失败第 {} 次 ===", blocked);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("resource", "degrade-slow");
        data.put("explain", "blockHandler 触发：熔断已打开，本轮快速失败（没有真实发下游请求）");
        data.put("blockedCount", blocked);
        data.put("blockException", ex.getClass().getSimpleName());
        ApiResponse<Map<String, Object>> response = ApiResponse.fail(7001, "熔断降级：DegradeException（快速失败）");
        response.setData(data);
        return response;
    }

    /**
     * 异常比例（资源 degrade-exception-ratio，规则：异常比例 ≥50%，最小请求数 5，熔断 5s）。
     * <p>
     * 同一个接口奇数次抛异常、偶数次成功，约 50% 的异常比例刚好压着阈值，
     * 连打十几次就能看到它打开。异常真的抛出去（而不是内部吞掉）是这里的关键。
     */
    public ApiResponse<Map<String, Object>> exceptionRatio() {
        int seq = RATIO_CALLS.incrementAndGet();
        if (seq % 2 == 1) {
            throw new IllegalStateException("模拟下游返回错误码：第 " + seq + " 次调用失败（异常比例 50%）");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("resource", "degrade-exception-ratio");
        data.put("explain", "第 " + seq + " 次调用成功（奇数次失败、偶数次成功）");
        data.put("seq", seq);
        return ApiResponse.ok(data);
    }

    /** 异常比例资源的 fallback：业务异常兜底（区别于熔断打开时的 blockHandler） */
    public ApiResponse<Map<String, Object>> exceptionRatioFallback(Throwable t) {
        if (t instanceof BlockException) {
            throw ((BlockException) t).toRuntimeException();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("resource", "degrade-exception-ratio");
        data.put("explain", "fallback 触发：业务异常兜底；Sentinel 同时把这次调用记成 error，用于异常比例统计");
        data.put("exception", t.getClass().getSimpleName());
        ApiResponse<Map<String, Object>> response = ApiResponse.fail(7002, "业务异常已降级兜底（fallback）");
        response.setData(data);
        return response;
    }

    /** 异常比例资源的 blockHandler：异常比例超阈值、熔断打开后触发 */
    public ApiResponse<Map<String, Object>> exceptionRatioBlockHandler(BlockException ex) {
        int blocked = RATIO_BLOCKED.incrementAndGet();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("resource", "degrade-exception-ratio");
        data.put("explain", "blockHandler 触发：异常比例超 50%，熔断打开，快速失败");
        data.put("blockedCount", blocked);
        data.put("blockException", ex.getClass().getSimpleName());
        ApiResponse<Map<String, Object>> response = ApiResponse.fail(7001, "熔断降级：DegradeException（快速失败）");
        response.setData(data);
        return response;
    }

    /**
     * 异常数（资源 degrade-exception-count，规则：异常数 ≥5，最小请求数 5，熔断 15s）。
     * <p>
     * 与异常比例的区别：低流量接口（比如每分钟 5 次）算比例毫无意义——每次失败都是 100%，
     * 但只失败 1 次就熔断显然过激；用「绝对条数」更符合这类接口的直觉。
     * 默认 {@code alwaysFail=true}，连打几次即可堆够 5 条异常；用
     * {@code /degrade/count-mode?alwaysFail=false} 可以切成成功，观察半开恢复。
     */
    public ApiResponse<Map<String, Object>> exceptionCount() {
        if (alwaysFail) {
            throw new IllegalArgumentException("模拟持续失败：异常数累加到 5 即熔断（alwaysFail=true）");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("resource", "degrade-exception-count");
        data.put("explain", "本次调用成功（alwaysFail=false），用于观察熔断半开后的恢复");
        return ApiResponse.ok(data);
    }

    /** 异常数资源的 fallback */
    public ApiResponse<Map<String, Object>> exceptionCountFallback(Throwable t) {
        if (t instanceof BlockException) {
            throw ((BlockException) t).toRuntimeException();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("resource", "degrade-exception-count");
        data.put("explain", "fallback 触发：业务异常兜底，同时累加异常计数");
        data.put("exception", t.getClass().getSimpleName());
        ApiResponse<Map<String, Object>> response = ApiResponse.fail(7002, "业务异常已降级兜底（fallback）");
        response.setData(data);
        return response;
    }

    /** 异常数资源的 blockHandler：异常条数达 5 条后熔断 15s */
    public ApiResponse<Map<String, Object>> exceptionCountBlockHandler(BlockException ex) {
        int blocked = COUNT_BLOCKED.incrementAndGet();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("resource", "degrade-exception-count");
        data.put("explain", "blockHandler 触发：异常条数达到 5，熔断 15s");
        data.put("blockedCount", blocked);
        data.put("blockException", ex.getClass().getSimpleName());
        ApiResponse<Map<String, Object>> response = ApiResponse.fail(7001, "熔断降级：DegradeException（快速失败）");
        response.setData(data);
        return response;
    }

    /** 切换异常数资源的成功/失败模式，用于观察熔断的自动恢复（半开） */
    public Map<String, Object> switchCountMode(boolean fail) {
        alwaysFail = fail;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("resource", "degrade-exception-count");
        data.put("alwaysFail", alwaysFail);
        return data;
    }

    /** 供 /sentinel/status 使用 */
    public Map<String, Object> counters() {
        Map<String, Object> counters = new LinkedHashMap<>();
        counters.put("degrade-slow 快速失败次数", SLOW_BLOCKED.get());
        counters.put("degrade-exception-ratio 快速失败次数", RATIO_BLOCKED.get());
        counters.put("degrade-exception-count 快速失败次数", COUNT_BLOCKED.get());
        counters.put("degrade-exception-ratio 调用序号", RATIO_CALLS.get());
        counters.put("degrade-exception-count alwaysFail", alwaysFail);
        return counters;
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
