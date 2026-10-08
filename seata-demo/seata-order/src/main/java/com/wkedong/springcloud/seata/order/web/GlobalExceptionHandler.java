package com.wkedong.springcloud.seata.order.web;

import io.seata.core.context.RootContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一异常输出：接口返回错误时，把 XID 一并带出来，便于对着日志找全局事务。
 *
 * @author wkedong
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handle(Exception e) {
        String xid = RootContext.getXID();
        log.error("下单失败：xid={}, 原因={}", xid, e.getMessage(), e);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 50000);
        body.put("message", e.getMessage());
        body.put("xid", xid);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
