package com.wkedong.springcloud.serviceconsumer.feign.service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

/**
 * Feign multipart 透传集成测试。
 * <p>
 * 2021.0.x 迁移说明：Spring 5 已移除 CommonsMultipartFile，
 * 构造测试文件改用 spring-test 的 MockMultipartFile；
 * 原 demo 硬编码的 Windows 路径（D:\1.jpg）改为内存构造。
 * <p>
 * 该用例依赖完整运行环境（eureka/config/service-producer 在线），
 * 默认禁用，需要联调时移除 @Disabled 手工执行。
 */
@Disabled("需要完整运行环境：eureka(6060)/config(6010)/service-producer(6070) 全部在线")
@SpringBootTest
class FeignServiceTest {

    private final Logger logger = LoggerFactory.getLogger(FeignServiceTest.class);

    @Autowired
    FeignService feignService;

    @Test
    void testFeignFile() {
        MultipartFile file = new MockMultipartFile(
                "file", "1.jpg", "image/jpeg",
                "feign multipart test content".getBytes(StandardCharsets.UTF_8));
        String result = feignService.testFeignFile(file);
        logger.info(result);
    }
}
