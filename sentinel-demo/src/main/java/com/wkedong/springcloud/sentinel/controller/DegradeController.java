package com.wkedong.springcloud.sentinel.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.wkedong.springcloud.sentinel.service.DegradeDemoService;
import com.wkedong.springcloud.sentinel.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 熔断降级入口：三种 {@code DegradeRule} 各一个资源，互不干扰。
 * <p>
 * 怎么用 curl 观察「熔断打开 → 快速失败」：
 * <ol>
 *   <li>先并发压十几下 {@code /degrade/slow}（每次真实耗时 ~800ms），堆够最小请求数并让慢调用比例达标；</li>
 *   <li>熔断打开后继续请求，耗时从 ~800ms 掉到 ~5ms，响应里 code=7001、blockException=DegradeException。</li>
 * </ol>
 * 耗时骤降就是「快速失败」最直观的证据——它证明请求根本没走到业务方法，也没发给下游。
 * <p>
 * 三种 grade 的适用场景：
 * <ul>
 *   <li>慢调用比例（RT）：下游只是「变慢」但没报错时，唯一能提前止损的规则；</li>
 *   <li>异常比例：流量足够大（窗口内请求数够多）时最稳，抗偶发抖动；</li>
 *   <li>异常数：低流量接口用，避免「1 个请求失败 = 100% 异常率」的误判。</li>
 * </ul>
 *
 * @author wkedong
 */
@RestController
@RequestMapping("/degrade")
public class DegradeController {

    private final DegradeDemoService degradeDemoService;

    public DegradeController(DegradeDemoService degradeDemoService) {
        this.degradeDemoService = degradeDemoService;
    }

    /**
     * 慢调用（800ms）→ 慢调用比例 ≥50%（最小请求数 10）→ 熔断 10s。
     * <pre>for i in $(seq 1 12); do curl -s "http://127.0.0.1:8220/degrade/slow" & done; wait</pre>
     */
    @GetMapping("/slow")
    @SentinelResource(value = "degrade-slow", blockHandler = "slowBlockHandler")
    public ApiResponse<Map<String, Object>> slow() {
        return degradeDemoService.slow();
    }

    /**
     * 异常比例（奇数次抛异常）→ 异常比例 ≥50%（最小请求数 5）→ 熔断 5s。
     * <pre>for i in $(seq 1 10); do curl -s http://127.0.0.1:8220/degrade/exception-ratio; echo; done</pre>
     */
    @GetMapping("/exception-ratio")
    @SentinelResource(value = "degrade-exception-ratio",
            blockHandler = "exceptionRatioBlockHandler", fallback = "exceptionRatioFallback")
    public ApiResponse<Map<String, Object>> exceptionRatio() {
        return degradeDemoService.exceptionRatio();
    }

    /**
     * 异常数（默认持续失败）→ 异常数 ≥5 → 熔断 15s。
     * <pre>for i in $(seq 1 8); do curl -s http://127.0.0.1:8220/degrade/exception-count; echo; done</pre>
     */
    @GetMapping("/exception-count")
    @SentinelResource(value = "degrade-exception-count",
            blockHandler = "exceptionCountBlockHandler", fallback = "exceptionCountFallback")
    public ApiResponse<Map<String, Object>> exceptionCount() {
        return degradeDemoService.exceptionCount();
    }

    /** 切换异常数资源的成功/失败模式，用于观察熔断半开后的自动恢复 */
    @GetMapping("/count-mode")
    public ApiResponse<Map<String, Object>> countMode(@RequestParam(value = "alwaysFail", defaultValue = "true") boolean alwaysFail) {
        return ApiResponse.ok(degradeDemoService.switchCountMode(alwaysFail));
    }

    // ------------------- blockHandler / fallback -------------------

    /** 慢调用资源的 blockHandler：熔断打开后快速失败 */
    public ApiResponse<Map<String, Object>> slowBlockHandler(BlockException ex) {
        return degradeDemoService.slowBlockHandler(ex);
    }

    public ApiResponse<Map<String, Object>> exceptionRatioBlockHandler(BlockException ex) {
        return degradeDemoService.exceptionRatioBlockHandler(ex);
    }

    public ApiResponse<Map<String, Object>> exceptionRatioFallback(Throwable throwable) {
        return degradeDemoService.exceptionRatioFallback(throwable);
    }

    public ApiResponse<Map<String, Object>> exceptionCountBlockHandler(BlockException ex) {
        return degradeDemoService.exceptionCountBlockHandler(ex);
    }

    public ApiResponse<Map<String, Object>> exceptionCountFallback(Throwable throwable) {
        return degradeDemoService.exceptionCountFallback(throwable);
    }
}
