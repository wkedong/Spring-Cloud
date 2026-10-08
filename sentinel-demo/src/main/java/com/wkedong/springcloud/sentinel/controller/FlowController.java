package com.wkedong.springcloud.sentinel.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.wkedong.springcloud.sentinel.service.FlowDemoService;
import com.wkedong.springcloud.sentinel.web.ApiResponse;
import com.wkedong.springcloud.sentinel.web.DemoLimits;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.util.Map;

/**
 * 注解式流控入口：{@code @SentinelResource} 的两种兜底方式。
 * <p>
 * 为什么把 {@code blockHandler} 和 {@code fallback} 都写上：这是本模块最重要的教学点。
 * <pre>
 * @SentinelResource(value = "flow-qps", blockHandler = "qpsBlockHandler", fallback = "businessExceptionFallback")
 *                    ↑ 资源名（规则按它匹配）  ↑ 被规则拦住时    ↑ 方法抛业务异常时
 * </pre>
 * 同一份配置下：
 * <ul>
 *   <li>连打 {@code /demo/qps} 触发流控 → 走 {@code qpsBlockHandler}（fallback 不动）</li>
 *   <li>打 {@code /demo/exception} 触发业务异常 → 走 {@code businessExceptionFallback}（blockHandler 不动）</li>
 * </ul>
 * 一句话记：**规则拦的走 blockHandler，代码抛的走 fallback**。
 * <p>
 * 注意 blockHandler / fallback 必须在同一个类里（或通过 {@code blockHandlerClass} 指定静态方法类），
 * 且方法签名与原方法一致、返回值类型一致、public 修饰。
 *
 * @author wkedong
 */
@RestController
@RequestMapping("/demo")
@Validated
public class FlowController {

    private final FlowDemoService flowDemoService;

    public FlowController(FlowDemoService flowDemoService) {
        this.flowDemoService = flowDemoService;
    }

    /**
     * 触发流控（QPS=2）：连续请求可以看到前 2 个通过、第 3 个开始被 blockHandler 接住。
     * <pre>for i in $(seq 1 10); do curl -s "http://127.0.0.1:8220/demo/qps?note=第$i次"; echo; done</pre>
     */
    @GetMapping("/qps")
    @SentinelResource(value = "flow-qps", blockHandler = "qpsBlockHandler", fallback = "businessExceptionFallback")
    public ApiResponse<Map<String, Object>> qps(@RequestParam(value = "note", required = false) String note) {
        return flowDemoService.qps(note);
    }

    /**
     * 触发业务异常走 fallback：同一个接口奇数次抛异常、偶数次成功，一条命令就能看到两种分支。
     * <pre>for i in 1 2 3 4; do curl -s http://127.0.0.1:8220/demo/exception; echo; done</pre>
     */
    @GetMapping("/exception")
    @SentinelResource(value = "flow-qps", blockHandler = "qpsBlockHandler", fallback = "businessExceptionFallback")
    public ApiResponse<Map<String, Object>> exception(@RequestParam(value = "note", required = false) String note) {
        return flowDemoService.businessException(note);
    }

    /**
     * 并发线程数模式：4 个请求同时打进来（每个睡 1000ms），只有 2 个能进去。
     * <pre>for i in 1 2 3 4; do curl -s "http://127.0.0.1:8220/demo/concurrent?sleepMs=1000" & done; wait</pre>
     */
    @GetMapping("/concurrent")
    @SentinelResource(value = "flow-concurrent", blockHandler = "concurrentBlockHandler")
    public ApiResponse<Map<String, Object>> concurrent(
            @RequestParam(value = "sleepMs", defaultValue = "1000")
            @Min(DemoLimits.MIN_SLEEP_MS) @Max(DemoLimits.MAX_SLEEP_MS) long sleepMs) {
        return flowDemoService.concurrent(sleepMs);
    }

    /**
     * 排队等待（RateLimiterController）：QPS 同样是 2，但第 3 个请求会「等」而不是被拒——
     * 观察响应时间变长（而不是出现 code=7001）。
     * <pre>for i in $(seq 1 5); do curl -s -o /dev/null -w "%{time_total}s\n" http://127.0.0.1:8220/demo/queue; done</pre>
     */
    @GetMapping("/queue")
    @SentinelResource(value = "flow-queue", blockHandler = "queueBlockHandler")
    public ApiResponse<Map<String, Object>> queue() {
        return flowDemoService.queue();
    }

    /**
     * 稳定复现 fallback（业务异常）：第一次必抛异常 → fallback 接住，之后成功。
     * <pre>curl http://127.0.0.1:8220/demo/reset; curl http://127.0.0.1:8220/demo/fallback-demo</pre>
     * 输出里 code=7002、exception=IllegalStateException，而 blockHandler 计数保持 0——
     * 这就证明「业务异常走 fallback，不走 blockHandler」。
     */
    @GetMapping("/fallback-demo")
    @SentinelResource(value = "flow-qps", blockHandler = "qpsBlockHandler", fallback = "businessExceptionFallback")
    public ApiResponse<Map<String, Object>> fallbackDemo(@RequestParam(value = "note", required = false) String note) {
        return flowDemoService.alwaysThrowFirst(note);
    }

    /** 重置计数，方便反复实验（不影响 Sentinel 自己的滑动窗口统计） */
    @GetMapping("/reset")
    public ApiResponse<Map<String, Object>> reset() {
        return ApiResponse.ok(flowDemoService.resetCounters());
    }

    // ------------------------------------------------------------------
    // 以下方法即 blockHandler / fallback：它们本身也能是普通的 Spring Bean 方法，
    // 放在 controller 里是为了让「原方法 + 兜底方法」紧挨着，方便对照阅读。
    // ------------------------------------------------------------------

    /** blockHandler：被流控规则拦截时执行（注意末尾多一个 BlockException 参数） */
    public ApiResponse<Map<String, Object>> qpsBlockHandler(String note, BlockException ex) {
        return flowDemoService.qpsBlockHandler(note, ex);
    }

    /** fallback：业务方法抛异常时执行（注意末尾多一个 Throwable 参数） */
    public ApiResponse<Map<String, Object>> businessExceptionFallback(String note, Throwable throwable) {
        return flowDemoService.businessExceptionFallback(note, throwable);
    }

    public ApiResponse<Map<String, Object>> concurrentBlockHandler(long sleepMs, BlockException ex) {
        return flowDemoService.concurrentBlockHandler(sleepMs, ex);
    }

    public ApiResponse<Map<String, Object>> queueBlockHandler(BlockException ex) {
        return flowDemoService.queueBlockHandler(ex);
    }
}
