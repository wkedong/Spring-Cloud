package com.wkedong.springcloud.sentinel.handler;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowException;
import com.alibaba.csp.sentinel.slots.system.SystemBlockException;
import com.wkedong.springcloud.sentinel.web.ApiResponse;
import com.wkedong.springcloud.sentinel.web.DemoLimits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 业务异常兜底（第三道也是最后一道）。
 * <p>
 * 为什么还要在 Sentinel 模块里加全局异常处理器：为了把「三种失败」区分清楚——
 * <ol>
 *   <li><b>URL 资源被拦</b>（进 Controller 之前）：由 {@code SentinelBlockExceptionHandler} 处理，HTTP 429；</li>
 *   <li><b>@SentinelResource 资源被拦</b>：配了 blockHandler 走 blockHandler；</li>
 *   <li><b>没配任何兜底</b>：落到这里。默认会被 Spring MVC 包成 500 错误页，本类把
 *       BlockException 单独拎出来返回 429，业务异常返回统一 JSON。</li>
 * </ol>
 * 一个必须澄清的细节：自 Sentinel 1.8 起 {@code BlockException} 是**受检异常**
 * （{@code extends Exception}，不是 {@code RuntimeException} 也不是 {@code Error}），
 * 所以它从 Controller 方法逃出去时会被 Spring MVC 包装成
 * {@code NestedServletException} 并变成 HTTP 500 ——「配了 @SentinelResource 没配 blockHandler」
 * 的接口被限流时，调用方看到的就是 500。这跟「限流是正常业务行为」的直觉完全相反，
 * 所以务必显式提供 blockHandler（或像下面这样加一个 429 分支）。
 * <p>
 * {@code @ExceptionHandler(BlockException.class)} 比 {@code Exception.class} 更具体，
 * Spring 会优先选中它，两条分支不会互相抢。
 *
 * @author wkedong
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * BlockException 兜底：只对「没配 blockHandler 的 @SentinelResource 方法」生效。
     * <p>
     * 为什么返回 429 而不是 200：被限流时返回 200 会让调用方以为「这个请求成功了」，
     * 监控里也看不出异常。429 Too Many Requests 是语义最准确的信号。
     */
    @ExceptionHandler(BlockException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public ApiResponse<Map<String, Object>> handleBlockException(BlockException e) {
        log.warn("=== BlockException 逃到了全局处理器（说明该方法没配 blockHandler）: {} ===", e.getClass().getSimpleName());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("blockType", blockTypeName(e));
        data.put("blockException", e.getClass().getSimpleName());
        data.put("hint", "该方法没有配置 blockHandler，BlockException 逃出了业务方法；"
                + "加上 blockHandler 就能在方法内优雅返回，而不是走异常处理器");
        ApiResponse<Map<String, Object>> response = ApiResponse.fail(7001, "被 Sentinel 规则拦截（flow limiting）");
        response.setData(data);
        return response;
    }

    /**
     * 入参校验失败：返回 400（信任边界上的拒绝，属于「调用方的错」）。
     * <p>
     * 为什么它必须排在 {@code Exception.class} 之前单独处理：校验失败本质上与限流一样是
     * 「预料之中的拒绝」，但语义完全不同——限流是 429（我挡的），参数非法是 400（你传错的）。
     * 如果统一按 {@code Exception.class} 兜成 HTTP 200 + 业务码，调用方会误以为请求被受理了，
     * 排查时也区分不出「参数写错」和「服务端内部出错」。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Map<String, Object>> handleConstraintViolation(ConstraintViolationException e) {
        Map<String, Object> data = new LinkedHashMap<>();
        List<String> violations = new ArrayList<>();
        for (ConstraintViolation<?> violation : e.getConstraintViolations()) {
            violations.add(violation.getPropertyPath() + ": " + violation.getMessage());
        }
        data.put("violations", violations);
        data.put("hint", "入参超出允许范围，被信任边界挡下（例如 sleepMs 上限 " + DemoLimits.MAX_SLEEP_MS + "ms）");
        ApiResponse<Map<String, Object>> response = ApiResponse.fail(7005, "入参校验失败");
        response.setData(data);
        return response;
    }

    /** 未被 Sentinel 兜住的普通业务异常 */
    @ExceptionHandler(Exception.class)
    public ApiResponse<Map<String, Object>> handleException(Exception e) {
        log.warn("=== 业务异常（未配置 Sentinel fallback 的路径）: {} ===", e.getMessage());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("exception", e.getClass().getName());
        data.put("message", e.getMessage());
        data.put("hint", "该异常没有被 @SentinelResource 的 fallback 兜住，说明它发生在未受保护的方法上");
        ApiResponse<Map<String, Object>> response = ApiResponse.fail(7004, "业务异常（global handler）");
        response.setData(data);
        return response;
    }

    /** 把 BlockException 的种类翻译成「是哪一类规则拦的」，排查时第一个要回答的问题 */
    private String blockTypeName(BlockException e) {
        if (e instanceof FlowException) {
            return "流控规则 FlowException";
        }
        if (e instanceof DegradeException) {
            return "熔断降级 DegradeException";
        }
        if (e instanceof ParamFlowException) {
            return "热点参数 ParamFlowException";
        }
        if (e instanceof SystemBlockException) {
            return "系统保护 SystemBlockException";
        }
        return "其它 BlockException";
    }
}
