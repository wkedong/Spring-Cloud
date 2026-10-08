package com.wkedong.springcloud.sentinel.handler;

import com.alibaba.csp.sentinel.adapter.spring.webmvc.callback.BlockExceptionHandler;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowException;
import com.alibaba.csp.sentinel.slots.system.SystemBlockException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wkedong.springcloud.sentinel.web.ApiResponse;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * URL 资源被拦时的统一响应（第二道兜底）。
 * <p>
 * 为什么还需要它：{@code @SentinelResource} 只保护注解标注的那个方法，
 * 而 Sentinel 的 web 适配器还会为每个 URL 建一个资源（本模块实测资源名是**纯路径**，如 {@code /system/probe}）。
 * 当 URL 层的资源被拦（典型场景：系统规则、网关式全局限流），
 * 异常发生在进入 Controller 之前，注解的 blockHandler 根本来不及生效——
 * 这时就由 {@link BlockExceptionHandler} 决定返回什么。
 * <p>
 * 为什么不用默认实现：Sentinel 自带的默认处理器返回的是纯文本
 * {@code Blocked by Sentinel (flow limiting)} + HTTP 429。文本对 curl 友好，
 * 但对前端不友好（要额外判断 Content-Type）。这里保留 429 状态码，
 * 同时把响应体换成与其它接口一致的 JSON，并带上「是哪一类规则拦的」。
 * <p>
 * 想看默认文本效果？把本类上的 {@code @Component} 注释掉重启即可——
 * 文档「动手验证」一节记录的就是默认实现的输出。
 *
 * @author wkedong
 */
@Component
public class SentinelBlockExceptionHandler implements BlockExceptionHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, BlockException e) throws IOException {
        // 被拦不是「系统坏了」，而是「现在不接」：用 429 Too Many Requests 语义最准确，
        // 同时保证客户端能从 Retry-After / 响应体里知道该怎么办
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> data = new LinkedHashMap<>();
        // 不用三元表达式：显式分支
        // 为什么要判空：SystemBlockException 这类异常是用「只传 limitApp」的构造函数造的，
        // 父类 rule 字段为 null（Sentinel 1.8.6 的 LogSlot 正是因此抛 NPE），这里必须防住
        if (e.getRule() == null) {
            data.put("resource", request.getRequestURI());
        } else {
            data.put("resource", e.getRule().getResource());
        }
        data.put("requestUri", request.getRequestURI());
        data.put("blockType", blockTypeName(e));
        data.put("blockException", e.getClass().getSimpleName());
        data.put("explain", "URL 层资源被 Sentinel 拦下（在进入 Controller 之前），"
                + "所以走的是 BlockExceptionHandler 而不是 @SentinelResource 的 blockHandler");

        ApiResponse<Map<String, Object>> body = ApiResponse.fail(7001,
                "被 Sentinel 规则拦截（flow limiting）");
        body.setData(data);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    /** 把 BlockException 的种类翻译成「是哪一类规则拦的」，这是排查时最先要问的问题 */
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
