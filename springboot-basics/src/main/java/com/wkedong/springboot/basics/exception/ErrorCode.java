package com.wkedong.springboot.basics.exception;

/**
 * 业务码约定（教学示例，实际项目按团队规范定义）。
 *
 * @author wkedong
 */
public final class ErrorCode {

    /** 参数错误 */
    public static final int BAD_REQUEST = 40000;
    /** 未认证/鉴权失败 */
    public static final int UNAUTHORIZED = 40100;
    /** 资源不存在 */
    public static final int NOT_FOUND = 40400;
    /** 通用业务失败（余额不足等） */
    public static final int BUSINESS_ERROR = 50000;
    /** 服务器内部错误 */
    public static final int INTERNAL_ERROR = 50001;

    private ErrorCode() {
    }
}
