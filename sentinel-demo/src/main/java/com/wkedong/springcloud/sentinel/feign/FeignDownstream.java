package com.wkedong.springcloud.sentinel.feign;

import com.wkedong.springcloud.sentinel.web.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 被 Sentinel 保护的声明式远程调用（{@code @FeignClient}）。
 * <p>
 * 为什么用 {@code url} 直连而不是服务名：本模块刻意不依赖注册中心（Nacos/Eureka 都不用），
 * 只要 8220 自己起来就能演示。{@code url="http://127.0.0.1:8220"} 表示「写死地址、跳过服务发现」，
 * 这也是 Feign 在无注册中心场景（对接第三方 HTTP 接口）下的常用形态。
 * <p>
 * 为什么降级要用 {@code fallbackFactory} 而不是 {@code fallback}：
 * {@code fallback} 只能返回一个固定兜底对象，拿不到异常原因；
 * {@code FallbackFactory} 能拿到触发降级的 {@code Throwable}（超时？连接拒绝？还是被 Sentinel 拦了？），
 * 便于日志排查与差异化兜底——生产上基本都用它。
 * <p>
 * {@code feign.sentinel.enabled=true}（见 application.yml）会让 Feign 的每个方法调用
 * 自动变成一个 Sentinel 资源（资源名形如 {@code GET http://127.0.0.1:8220/downstream/slow}），
 * 于是「限流/熔断规则」也能直接作用在远程调用上，不需要手写 try-catch。
 *
 * @author wkedong
 */
@FeignClient(name = "self-downstream", url = "http://127.0.0.1:8220",
        fallbackFactory = FeignDownstreamFallbackFactory.class)
public interface FeignDownstream {

    /** 调用本模块「故意慢」的下游：Feign 读超时 1000ms + Sentinel 默认熔断 1000ms 都会在这里触发降级 */
    @GetMapping("/downstream/slow")
    ApiResponse<Map<String, Object>> slow(@RequestParam("sleepMs") long sleepMs);

    /** 调用本模块「故意失败」的下游：HTTP 500 → Feign 抛 FeignException → 走 FallbackFactory */
    @GetMapping("/downstream/error")
    ApiResponse<Map<String, Object>> error();
}
