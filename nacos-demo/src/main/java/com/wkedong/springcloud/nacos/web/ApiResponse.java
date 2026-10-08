package com.wkedong.springcloud.nacos.web;

import org.slf4j.MDC;

/**
 * 统一响应体（本模块自带一份，不与其它模块共享代码）。
 * <p>
 * 为什么每个 HTTP 端点都套这一层：
 * <ul>
 *   <li>调用方只需解析一种结构，curl 验证时也好写 jq 过滤；</li>
 *   <li>HTTP 状态码表达「协议层」结果，业务码 {@code code} 表达「业务层」结果，两者解耦；</li>
 *   <li>带上 traceId：排查「负载均衡打到哪台」这类问题时，一次请求的头尾日志能串起来。</li>
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
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> response = new ApiResponse<T>();
        response.code = 0;
        response.message = "success";
        response.data = data;
        response.traceId = MDC.get(TraceIdFilter.TRACE_ID_KEY);
        return response;
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        ApiResponse<T> response = new ApiResponse<T>();
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
