package com.wkedong.springcloud.serviceconsumer.feign.controller;

import com.wkedong.springcloud.serviceconsumer.feign.exception.DownstreamServiceException;
import com.wkedong.springcloud.serviceconsumer.feign.service.FeignService;
import com.wkedong.springcloud.serviceconsumer.feign.service.ProducerFallbackClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * FeignDemo
 * <p>
 * 2021.0.x 迁移说明：Spring 5 已移除 org.springframework.web.multipart.commons.CommonsMultipartFile，
 * 原「落盘 → DiskFileItem → CommonsMultipartFile 二次包装」的老写法不再可用；
 * feign-form 3.8.0 的 SpringFormEncoder 原生支持 Spring MultipartFile，
 * 落盘留档后直接把原始 MultipartFile 交给 Feign 透传即可。
 * <p>
 * 教学扩展端点见本类后半部分（对应 docs/17 Feign 进阶）：
 * 请求头拦截、FULL 日志、自定义 ErrorDecoder、读超时、FallbackFactory 降级。
 *
 * @author wkedong
 * 2019/1/14
 */
@RestController
public class FeignController {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    FeignService feignService;

    @Autowired
    ProducerFallbackClient producerFallbackClient;

    @GetMapping("/testFeign")
    public String testFeign() {
        logger.info("===<call testFeign>===");
        return feignService.testFeign();
    }

    @PostMapping(value = "/testFeignFile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String testFeignFile(@RequestParam("file") MultipartFile file) {
        logger.info("===<call testFeignFile>===");
        if (file != null && !file.isEmpty()) {
            try {
                // 顺序要点：先 Feign 转发、再落盘留档。
                // transferTo() 会把 Tomcat 的 multipart 临时文件移动走，之后原 MultipartFile
                // 不可再读（实测报 FileNotFoundException）——这是 Spring multipart 的经典坑。
                String result = feignService.testFeignFile(file);
                File dir = new File(System.getProperty("java.io.tmpdir"), "tempFile");
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                File tempFile = new File(dir, file.getOriginalFilename());
                file.transferTo(tempFile); //归档（与旧 demo 行为一致）
                logger.info("file forwarded and saved to {}", tempFile.getAbsolutePath());
                return result;
            } catch (IllegalStateException | IOException e) {
                logger.error("multipart 文件处理失败", e);
            }
        }
        return "文件有误";
    }

    // ======================= 教学扩展：Feign 进阶 =======================

    /**
     * 请求头拦截器验证：producer 会回显收到的请求头。
     * 期望在 highlight 里看到 X-From（我们注入的）与 X-B3-TraceId（Sleuth 自动注入的）。
     */
    @GetMapping("/testFeignHeaderEcho")
    public Map<String, Object> testFeignHeaderEcho() {
        logger.info("===<call testFeignHeaderEcho>===");
        return feignService.echoHeaders();
    }

    /**
     * 读超时验证：producer 睡 5 秒，Feign 的 readTimeout 配的是 2 秒。
     * 期望：抛异常（超时），并且耗时约 2 秒——这就是「超时必须小于上游超时预算」的实证。
     */
    @GetMapping("/testFeignTimeout")
    public Map<String, Object> testFeignTimeout(@RequestParam(value = "seconds", defaultValue = "5") int seconds) {
        logger.info("===<call testFeignTimeout>=== 下游将睡 {} 秒，本客户端读超时 2 秒", seconds);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        long start = System.currentTimeMillis();
        try {
            String result = feignService.testSlow(seconds);
            data.put("success", true);
            data.put("result", result);
        } catch (Exception e) {
            data.put("success", false);
            data.put("exceptionType", e.getClass().getName());
            data.put("message", e.getMessage());
        }
        data.put("costMillis", System.currentTimeMillis() - start);
        return data;
    }

    /** 自定义 ErrorDecoder 验证：下游 500 → 应被翻译成 DownstreamServiceException */
    @GetMapping("/testFeignErrorDecode")
    public Map<String, Object> testFeignErrorDecode() {
        logger.info("===<call testFeignErrorDecode>===");
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        try {
            String result = feignService.testError();
            data.put("success", true);
            data.put("result", result);
        } catch (DownstreamServiceException e) {
            data.put("success", false);
            data.put("exceptionType", e.getClass().getSimpleName());
            data.put("message", e.getMessage());
            data.put("hint", "被自定义 ErrorDecoder 翻译成了业务语义异常，而不是裸的 FeignException");
        } catch (Exception e) {
            data.put("success", false);
            data.put("exceptionType", e.getClass().getName());
            data.put("message", e.getMessage());
        }
        return data;
    }

    /** FallbackFactory 降级验证：下游 500 → 降级工厂兜底（需要 feign.circuitbreaker.enabled=true） */
    @GetMapping("/testFeignFallback")
    public String testFeignFallback() {
        logger.info("===<call testFeignFallback>===");
        return producerFallbackClient.testError();
    }

    /** FallbackFactory 降级验证（超时场景）：下游睡 5 秒，读超时 2 秒触发降级 */
    @GetMapping("/testFeignFallbackTimeout")
    public String testFeignFallbackTimeout(@RequestParam(value = "seconds", defaultValue = "5") int seconds) {
        logger.info("===<call testFeignFallbackTimeout>===");
        return producerFallbackClient.testSlow(seconds);
    }
}
