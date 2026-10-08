package com.wkedong.springboot.basics.web;

import org.slf4j.MDC;

import java.time.Instant;

/**
 * 统一响应体。
 * <p>
 * 为什么需要它（教学讨论点）：
 * <ul>
 *   <li>前端/调用方只需解析一种结构，不用为每个接口写不同分支；</li>
 *   <li>HTTP 状态码表达「协议层」结果，业务码（code）表达「业务层」结果，两者解耦；</li>
 *   <li>带上 traceId：报错时把前端截图里的 traceId 直接丢给后端，就能在日志里定位整条链路。</li>
 * </ul>
 *
 * @param <T> 业务数据类型
 * @author wkedong
 */
public class ApiResponse<T> {

    /** 业务码：0 表示成功 */
    private int code;
    private String message;
    private T data;
    /** 请求追踪号（由 TraceIdFilter 写入 MDC） */
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
        response.traceId = MDC.get(TraceIdFilter.TRACE_ID_KEY);
        return response;
    }

    public static <T> ApiResponse<T> ok() {
        return ok(null);
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.code = code;
        response.message = message;
        response.traceId = MDC.get(TraceIdFilter.TRACE_ID_KEY);
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
