package com.wkedong.springcloud.security.web;

import org.slf4j.MDC;

import java.time.Instant;

/**
 * 统一响应体（本模块自带一份，不跨模块引用——模块之间共享代码应该抽公共包，而不是 import 别人的类）。
 * <p>
 * 结构与本仓库其他模块（如 springboot-basics 的 ApiResponse）保持一致：
 * <pre>
 * { "code": 0, "message": "success", "data": {...}, "traceId": null, "timestamp": 1700000000000 }
 * </pre>
 * 约定：{@code code == 0} 表示成功；HTTP 状态码表达协议层结果，业务码表达业务层结果。
 * <p>
 * 特别注意：**安全链路（401/403）不是靠这个类输出的**——
 * 认证/授权失败发生在 Spring Security 过滤器链里，请求根本进不到 Controller，
 * 所以必须由 {@code AuthenticationEntryPoint} / {@code AccessDeniedHandler} 自己写 JSON，
 * 但它们写出来的 JSON 结构与本类完全一致，调用方只需解析一种结构。
 *
 * @param <T> 业务数据类型
 * @author wkedong
 */
public class ApiResponse<T> {

    /** 业务码：0 成功；401 未认证；403 无权限 */
    private int code;
    private String message;
    private T data;
    /** 请求追踪号（有链路追踪时由过滤器写入 MDC，本模块不装 Sleuth 时为 null） */
    private String traceId;
    private long timestamp;

    public ApiResponse() {
        this.timestamp = Instant.now().toEpochMilli();
    }

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.code = 0;
        response.message = "success";
        response.data = data;
        response.traceId = MDC.get("traceId");
        return response;
    }

    public static <T> ApiResponse<T> ok() {
        return ok(null);
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.code = code;
        response.message = message;
        response.traceId = MDC.get("traceId");
        return response;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
