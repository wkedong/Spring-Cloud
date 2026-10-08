package com.wkedong.springcloud.sentinel.web;

import java.time.Instant;

/**
 * 统一响应体（本模块自带一份，不跨模块引用 springboot-basics，保持教学模块可独立打包）。
 * <p>
 * 为什么 Sentinel 演示里需要统一结构：
 * <ul>
 *   <li>被限流（blockHandler）与被降级（fallback）都是「正常协议层面的 200」，
 *       只有靠 code/message 才能区分「真成功 / 被规则拦截 / 业务异常兜底」；
 *   <li>curl 时一眼能从 JSON 里看到 code，不必去解析 HTTP 状态码。
 * </ul>
 * 约定：code=0 成功；7001 被 Sentinel 规则拦截；7002 业务异常走了 fallback。
 *
 * @param <T> 业务数据类型
 * @author wkedong
 */
public class ApiResponse<T> {

    /** 业务码：0 表示成功 */
    private int code;
    private String message;
    private T data;
    private long timestamp;

    public ApiResponse() {
        this.timestamp = Instant.now().toEpochMilli();
    }

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.code = 0;
        response.message = "success";
        response.data = data;
        return response;
    }

    public static <T> ApiResponse<T> ok() {
        return ok(null);
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.code = code;
        response.message = message;
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

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}