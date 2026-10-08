package com.wkedong.springboot.basics.web;

import com.wkedong.springboot.basics.exception.BusinessException;
import com.wkedong.springboot.basics.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import javax.servlet.http.HttpServletRequest;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 全局异常处理：把所有异常翻译成统一响应体。
 * <p>
 * 教学点：
 * <ol>
 *   <li><b>分层处理</b>：可预期失败（业务异常/校验失败）逐个精确处理；不可预期失败兜底 catch-all；</li>
 *   <li><b>校验错误要带字段</b>：MethodArgumentNotValidException 里能拿到「哪个字段、哪条约束」，
 *       直接返回给前端即可精确高亮表单项；</li>
 *   <li><b>兜底 handler 只给固定文案</b>：异常细节进日志（含 traceId 可关联），前台看不到堆栈；</li>
 *   <li><b>HTTP 状态码与业务码各司其职</b>：状态码让网关/监控识别失败，业务码给业务方判断。</li>
 * </ol>
 *
 * @author wkedong
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：可预期，记 WARN；HTTP 状态码按业务码映射，保持协议语义正确 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleBusiness(BusinessException ex,
                                                                          HttpServletRequest request) {
        HttpStatus status = toHttpStatus(ex.getCode());
        log.warn("业务异常：uri={}, code={}, status={}, message={}",
                request.getRequestURI(), ex.getCode(), status.value(), ex.getMessage());
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("uri", request.getRequestURI());
        ApiResponse<Map<String, Object>> response = ApiResponse.fail(ex.getCode(), ex.getMessage());
        response.setData(detail);
        return ResponseEntity.status(status).body(response);
    }

    /**
     * 业务码 → HTTP 状态码。
     * 教学点：401（未认证）与 404（不存在）如果都返回 400，会让网关/监控/前端路由难以区分，
     * 所以这里做一次显式映射；团队也可统一用「HTTP 200 + 业务码」的风格，关键是一致。
     */
    private HttpStatus toHttpStatus(int code) {
        if (code == ErrorCode.UNAUTHORIZED) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (code == ErrorCode.NOT_FOUND) {
            return HttpStatus.NOT_FOUND;
        }
        return HttpStatus.BAD_REQUEST;
    }

    /** @Valid 校验 @RequestBody 失败：把字段级错误一次性返回 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage, (a, b) -> a, LinkedHashMap::new));
        log.warn("参数校验失败：{}", fieldErrors);
        ApiResponse<Map<String, String>> response = ApiResponse.fail(ErrorCode.BAD_REQUEST, "参数校验失败");
        response.setData(fieldErrors);
        return response;
    }

    /** @Validated 校验方法参数（如 @RequestParam @Min(1)）失败 */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<String> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .collect(Collectors.joining("; "));
        log.warn("方法参数校验失败：{}", message);
        return ApiResponse.fail(ErrorCode.BAD_REQUEST, message);
    }

    /** 缺少必填请求参数 / 参数类型不匹配 / 请求体不是合法 JSON */
    @ExceptionHandler({MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<String> handleBadRequest(Exception ex) {
        log.warn("请求格式错误：{}", ex.getMessage());
        return ApiResponse.fail(ErrorCode.BAD_REQUEST, "请求参数格式错误");
    }

    /** 请求方法不支持（如把 POST 接口用 GET 调） */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ApiResponse<String> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ApiResponse.fail(ErrorCode.BAD_REQUEST, "请求方法不支持：" + ex.getMethod());
    }

    /**
     * 兜底：不可预期异常。
     * 固定文案 + ERROR 日志（含完整堆栈），traceId 由过滤器写入 MDC 一并落日志，便于定位。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<String> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("未预期异常：uri={}", request.getRequestURI(), ex);
        return ApiResponse.fail(ErrorCode.INTERNAL_ERROR, "服务器内部错误，请联系管理员并提供 traceId");
    }
}
