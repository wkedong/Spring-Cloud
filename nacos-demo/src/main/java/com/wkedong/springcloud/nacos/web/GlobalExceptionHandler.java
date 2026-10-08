package com.wkedong.springcloud.nacos.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 兜底异常处理：任何未捕获的异常都转成统一响应体。
 * <p>
 * 为什么最小闭环必须有它：控制器只要有一条路径漏了 try-catch，
 * 调用方就会拿到 Spring 默认的 Whitelabel / ProblemDetail 结构，
 * 「统一响应体」的约定当场破产。
 * <p>
 * 教学模块不隐藏异常信息（把 message 原样返回），生产环境应改为只回 traceId。
 *
 * @author wkedong
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("参数错误：{}", ex.getMessage());
        return ApiResponse.fail(400, "参数错误：" + ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception ex) {
        log.error("请求处理失败", ex);
        return ApiResponse.fail(500, ex.getClass().getSimpleName() + "：" + ex.getMessage());
    }
}
