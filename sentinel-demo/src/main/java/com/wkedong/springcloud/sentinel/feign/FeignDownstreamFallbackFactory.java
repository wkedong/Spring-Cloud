package com.wkedong.springcloud.sentinel.feign;

import com.wkedong.springcloud.sentinel.web.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Feign 降级工厂：Feign 与 Sentinel 整合后，远程调用失败/超时/被规则拦截时走这里。
 * <p>
 * 为什么要有它：远程调用是「必然会失败」的操作。没有降级，一次下游抖动就会把异常
 * 顺着调用链抛到用户面前；有了降级，调用方拿到的是一个确定的兜底结果。
 * <p>
 * 与 {@code blockHandler} 的关系：Feign 资源被 Sentinel 规则拦下时，抛出的
 * {@code BlockException}（受检异常，但同样会进入这里）——所以这里既处理
 * 「网络/超时异常」，也处理「被规则拦截」，靠异常类型区分后再给出不同文案。
 * <p>
 * 注意 FallbackFactory 的包是 {@code org.springframework.cloud.openfeign}：
 * 旧教程里的 {@code feign.hystrix.FallbackFactory} 随 Hystrix 一起退役了
 * （旧包在 OpenFeign 11 之后不再提供），照抄老代码会直接「找不到符号」。
 *
 * @author wkedong
 */
@Component
public class FeignDownstreamFallbackFactory implements FallbackFactory<FeignDownstream> {

    private static final Logger log = LoggerFactory.getLogger(FeignDownstreamFallbackFactory.class);

    @Override
    public FeignDownstream create(Throwable cause) {
        // cause 只用于日志；注意不要把它长期持有，避免「异常对象带着整条调用栈」占用内存
        log.warn("=== Feign 调用 self-downstream 失败，进入降级：{}: {} ===",
                cause.getClass().getName(), cause.getMessage());
        return new FeignDownstream() {

            @Override
            public ApiResponse<Map<String, Object>> slow(long sleepMs) {
                return degraded("feign→/downstream/slow", sleepMs, cause);
            }

            @Override
            public ApiResponse<Map<String, Object>> error() {
                return degraded("feign→/downstream/error", null, cause);
            }
        };
    }

    /** 统一构造降级响应：把「谁失败了、为什么失败」暴露出来，便于 curl 直接观察 */
    private ApiResponse<Map<String, Object>> degraded(String target, Long sleepMs, Throwable cause) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("target", target);
        data.put("explain", "FallbackFactory 触发：远程调用没成功，返回兜底结果（不再向下游发请求）");
        data.put("sleepMs", sleepMs);
        data.put("causeType", cause.getClass().getName());
        String message = cause.getMessage();
        data.put("causeMessage", message != null && message.length() > 120 ? message.substring(0, 120) + "..." : message);
        ApiResponse<Map<String, Object>> response = ApiResponse.fail(7003, "Feign 远程调用降级（fallbackFactory）");
        response.setData(data);
        return response;
    }
}
