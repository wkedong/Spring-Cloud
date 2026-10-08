package com.wkedong.springcloud.security.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MVC 层异常 → 统一响应体。
 * <p>
 * 为什么还需要它：{@code AuthenticationEntryPoint} 只能处理**过滤器链**里的失败；
 * 登录接口里的 {@code BadCredentialsException} 是在 Controller 调用栈里抛出的，
 * 需要这里翻译成 401，两边合起来才能做到「任何失败都是同一套 JSON」。
 * <p>
 * 注意：认证失败**只回一句笼统文案**（不区分「用户不存在」与「密码错误」），
 * 否则接口就成了「用户名探测器」——攻击者可以拿它枚举系统里有哪些账号。
 *
 * @author wkedong
 */
@RestControllerAdvice
public class RestExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RestExceptionHandler.class);

    /** 认证失败：401（HTTP 语义：请重新提供凭证） */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        // 日志里保留异常细节便于排查；返回给调用方的一律是统一文案
        log.warn("登录失败：{}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.fail(HttpStatus.UNAUTHORIZED.value(), "用户名或密码错误"));
    }

    /** 请求体校验失败：400，并回带字段级错误，方便前端精确高亮表单项 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(HttpStatus.BAD_REQUEST.value(), "参数校验失败：" + errors));
    }

    /**
     * 兜底：未预期异常 → 500。
     * <p>
     * 原则是「异常细节进日志，前台只给固定文案」：堆栈里常有 SQL、内网地址、密钥片段，
     * 直接回给调用方等于免费送情报。日志里有堆栈 + traceId，排查照样有据可依。
     * <p>
     * <b>关键一行：AccessDeniedException 必须原样抛出。</b>
     * {@code @PreAuthorize} 失败抛的是 AccessDeniedException，它要靠过滤器链上的
     * {@code ExceptionTranslationFilter} 才能变成 403；如果被这里的 catch-all 吃掉，
     * 用户就会收到一个莫名其妙的 500（这是「方法级鉴权返回 500 而不是 403」的最常见原因）。
     *
     * @param ex 任意异常
     * @return 统一 500 响应
     * @throws Exception 授权失败异常原样抛出，交给 Spring Security 转成 403
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) throws Exception {
        if (ex instanceof AccessDeniedException) {
            throw ex;
        }
        log.error("服务内部异常", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(HttpStatus.INTERNAL_SERVER_ERROR.value(), "服务内部错误，请联系管理员并提供时间点"));
    }
}
