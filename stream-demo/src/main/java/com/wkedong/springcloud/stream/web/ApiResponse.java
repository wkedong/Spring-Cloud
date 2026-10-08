package com.wkedong.springcloud.stream.web;

/**
 * 统一响应体（本模块自带一份，刻意不跨模块引用 springboot-basics 的同名类：
 * 教学模块之间保持「可单独拷贝运行」的独立性）。
 * <p>
 * 为什么需要它：
 * <ul>
 *   <li>调用方只需解析一种结构，不用为每个接口写不同分支；</li>
 *   <li>HTTP 状态码表达「协议层」结果，业务码 code 表达「业务层」结果，两者解耦；</li>
 *   <li>消息中间件是异步的：接口返回 code=0 只代表「已经投递给 Binder」，
 *       不代表下游已消费——这一点必须靠 data 里的 destination 与后续 /stream/stats 观察，
 *       所以把发送详情放进 data 而不是只返回一个布尔值。</li>
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
    private long timestamp;

    public ApiResponse() {
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> response = new ApiResponse<T>();
        response.code = 0;
        response.message = "success";
        response.data = data;
        return response;
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        ApiResponse<T> response = new ApiResponse<T>();
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
