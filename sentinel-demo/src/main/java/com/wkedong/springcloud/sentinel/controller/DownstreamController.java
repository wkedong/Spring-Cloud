package com.wkedong.springcloud.sentinel.controller;

import com.wkedong.springcloud.sentinel.web.ApiResponse;
import com.wkedong.springcloud.sentinel.web.DemoLimits;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 「故意慢 / 故意失败」的下游（被 {@code @FeignClient} 调用）。
 * <p>
 * 为什么下游就放在同一个模块里：教学 demo 不该依赖第二个进程，也不该依赖注册中心。
 * Feign 用 {@code url="http://127.0.0.1:8220"} 直连自己，就能演示
 * 「Feign 的读超时 → FallbackFactory 降级」和「Feign 与 Sentinel 整合」这两件事，
 * 顺带展示了「无注册中心时 Feign 怎么写」（对接第三方 HTTP 接口的常见形态）。
 * <p>
 * 注意：这两个端点是「故意坏」的，只用于演示，不要在生产代码里模仿。
 *
 * @author wkedong
 */
@RestController
@RequestMapping("/downstream")
@Validated
public class DownstreamController {

    /**
     * 故意慢：默认睡 1200ms，超过 Feign 的读超时（1000ms）与 Sentinel 默认熔断阈值（1000ms）。
     * <p>
     * {@code sleepMs} 必须带上限：它是「信任边界上的耗时参数」，不设上限就等于允许调用方
     * 用一个大数字长期占用 Tomcat 工作线程。
     */
    @GetMapping("/slow")
    public ApiResponse<Map<String, Object>> slow(
            @RequestParam(value = "sleepMs", defaultValue = "1200")
            @Min(DemoLimits.MIN_SLEEP_MS) @Max(DemoLimits.MAX_SLEEP_MS) long sleepMs) {
        long begin = System.currentTimeMillis();
        try {
            TimeUnit.MILLISECONDS.sleep(sleepMs);
        } catch (InterruptedException e) {
            // 恢复中断标志：吞掉中断会让上层永远不知道线程该退出
            Thread.currentThread().interrupt();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("downstream", "/downstream/slow");
        data.put("explain", "故意慢的下游：真实耗时 " + (System.currentTimeMillis() - begin) + "ms");
        data.put("sleepMs", sleepMs);
        return ApiResponse.ok(data);
    }

    /**
     * 故意失败：返回真正的 HTTP 500 → Feign 抛 FeignException → FallbackFactory 兜底。
     * <p>
     * 为什么这里用 {@code ResponseEntity} 手动指定 500，而不是直接 {@code throw new RuntimeException()}：
     * 本模块注册了 {@code @RestControllerAdvice} 全局异常处理器，它会把业务异常统一转成
     * <b>HTTP 200 + code=7004</b> 的 JSON。那样 Feign 收到的就是一个「成功响应」，
     * 既不会抛异常、也就永远走不到 FallbackFactory——这正是「统一异常处理」和
     * 「远程调用降级」容易打架的地方，必须在设计上说清楚：
     * 对下游要暴露成「失败」的接口，不能让它被统一异常处理器「美化」成 200。
     *
     * @return HTTP 500 + 错误 JSON，供 Feign 侧观察降级
     */
    @GetMapping("/error")
    public ResponseEntity<ApiResponse<Map<String, Object>>> error() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("downstream", "/downstream/error");
        data.put("explain", "故意失败的下游：返回真实 HTTP 500，触发 Feign 的 FeignException → FallbackFactory");
        data.put("exception", "IntentionalDownstreamException");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(500, "模拟下游 500：这个接口的设计意图就是失败"));
    }
}
