package com.wkedong.springboot.basics.web;

import com.wkedong.springboot.basics.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 综合演示接口：异常处理、白名单路径、优雅停机。
 * <pre>
 * GET /api/public/ping            免鉴权（拦截器白名单），用来验证拦截器生效范围
 * GET /api/demo/slow?seconds=5    慢接口：配合 Ctrl+C / kill -TERM 观察优雅停机
 * GET /api/demo/business-error    业务异常：HTTP 400 + 业务码，文案可读
 * GET /api/demo/system-error      系统异常：HTTP 500 + 固定文案（真实原因只在日志里）
 * </pre>
 *
 * @author wkedong
 */
@RestController
@RequestMapping("/api")
public class DemoController {

    private static final Logger log = LoggerFactory.getLogger(DemoController.class);

    @GetMapping("/public/ping")
    public ApiResponse<Map<String, Object>> ping() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pong", true);
        data.put("note", "本接口在拦截器白名单内，无需 X-Token");
        return ApiResponse.ok(data);
    }

    /**
     * 慢接口：优雅停机验证用。
     * 停机时（SIGTERM）Spring Boot 会等在处理的请求结束（默认 30s，本模块配 20s），
     * 期间新请求被拒绝，但本请求会正常返回。
     */
    @GetMapping("/demo/slow")
    public ApiResponse<Map<String, Object>> slow(@RequestParam(value = "seconds", defaultValue = "5") int seconds) {
        long start = System.currentTimeMillis();
        log.info("慢接口开始处理，预计 {} 秒", seconds);
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sleptSeconds", seconds);
        data.put("costMillis", System.currentTimeMillis() - start);
        data.put("note", "若本请求在停机过程中仍正常返回，说明优雅停机生效");
        return ApiResponse.ok(data);
    }

    @GetMapping("/demo/business-error")
    public ApiResponse<String> businessError() {
        throw new BusinessException("演示用业务异常：余额不足");
    }

    @GetMapping("/demo/system-error")
    public ApiResponse<String> systemError() {
        // 故意制造系统异常：前台只会看到固定文案 + traceId，堆栈进日志
        String value = null;
        return ApiResponse.ok(value.trim());
    }
}
