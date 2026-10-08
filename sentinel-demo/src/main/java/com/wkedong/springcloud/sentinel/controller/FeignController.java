package com.wkedong.springcloud.sentinel.controller;

import com.wkedong.springcloud.sentinel.feign.FeignDownstream;
import com.wkedong.springcloud.sentinel.web.ApiResponse;
import com.wkedong.springcloud.sentinel.web.DemoLimits;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Map;

/**
 * Feign + Sentinel 整合入口：远程调用的限流与降级。
 * <p>
 * 为什么要演示这个：真实系统里被流控/熔断保护的对象绝大多数是**远程调用**，
 * 而不是本地方法。{@code feign.sentinel.enabled=true} 之后，Feign 的每一次调用
 * 都会自动生成一个 Sentinel 资源（资源名如 {@code GET http://127.0.0.1:8220/downstream/slow}），
 * 于是规则可以直接作用在远程调用上，不用手写任何 try-catch。
 * <p>
 * 怎么看到降级：
 * <pre>
 * curl "http://127.0.0.1:8220/feign/call-slow?sleepMs=1200"   # 下游睡 1200ms > 读超时 1000ms → 降级
 * curl "http://127.0.0.1:8220/feign/call-error"               # 下游 500 → 降级
 * </pre>
 * 返回里 {@code code=7003}、{@code causeType} 会告诉你到底是「读超时」还是「下游 500」。
 *
 * @author wkedong
 */
@RestController
@RequestMapping("/feign")
@Validated
public class FeignController {

    private final FeignDownstream feignDownstream;

    public FeignController(FeignDownstream feignDownstream) {
        this.feignDownstream = feignDownstream;
    }

    /** 调用故意慢的下游：默认 1200ms > feign.client.config.default.readTimeout=1000 → FallbackFactory */
    @GetMapping("/call-slow")
    public ApiResponse<Map<String, Object>> callSlow(
            @RequestParam(value = "sleepMs", defaultValue = "1200")
            @Min(DemoLimits.MIN_SLEEP_MS) @Max(DemoLimits.MAX_SLEEP_MS) long sleepMs) {
        return feignDownstream.slow(sleepMs);
    }

    /** 调用故意失败的下游：HTTP 500 → FeignException → FallbackFactory */
    @GetMapping("/call-error")
    public ApiResponse<Map<String, Object>> callError() {
        return feignDownstream.error();
    }
}
