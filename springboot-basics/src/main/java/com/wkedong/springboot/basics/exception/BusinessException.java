package com.wkedong.springboot.basics.exception;

/**
 * 业务异常：表达「可预期的失败」（余额不足、参数非法、权限不够……）。
 * <p>
 * 与系统异常的区别（教学重点）：
 * <ul>
 *   <li>业务异常：日志记 WARN、文案可返回给前台、由统一异常处理器翻译成业务码；</li>
 *   <li>系统异常（NPE、SQL 错误）：日志记 ERROR + 完整堆栈、前台只给固定文案，
 *       **绝不**把 e.getMessage() 透传（既泄露实现细节，也没法指导用户）。</li>
 * </ul>
 *
 * @author wkedong
 */
public class BusinessException extends RuntimeException {

    /** 业务码，见 {@link ErrorCode} */
    private final int code;

    public BusinessException(String message) {
        this(ErrorCode.BUSINESS_ERROR, message);
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
