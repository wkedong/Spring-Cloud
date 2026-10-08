package com.wkedong.springcloud.serviceconsumer.feign.controller;

import com.wkedong.springcloud.serviceconsumer.feign.service.FeignService;
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
import java.nio.file.Files;

/**
 * FeignDemo
 * <p>
 * 2021.0.x 迁移说明：Spring 5 已移除 org.springframework.web.multipart.commons.CommonsMultipartFile，
 * 原「落盘 → DiskFileItem → CommonsMultipartFile 二次包装」的老写法不再可用；
 * feign-form 3.8.0 的 SpringFormEncoder 原生支持 Spring MultipartFile，
 * 落盘留档后直接把原始 MultipartFile 交给 Feign 透传即可。
 *
 * @author wkedong
 * 2019/1/14
 */
@RestController
public class FeignController {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    FeignService feignService;

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

}
