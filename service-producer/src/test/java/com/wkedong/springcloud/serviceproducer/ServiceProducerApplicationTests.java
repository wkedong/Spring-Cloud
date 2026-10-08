package com.wkedong.springcloud.serviceproducer;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Boot 2.4+ 的 spring-boot-starter-test 只带 JUnit 5（Jupiter），
 * 原 JUnit 4 的 @RunWith(SpringRunner.class) 写法一并移除。
 * <p>
 * 上下文测试依赖完整运行环境（eureka/config/MySQL 在线），无环境时禁用。
 */
@Disabled("需要完整运行环境：eureka(6060)/config(6010)/MySQL(3306) 在线")
@SpringBootTest
class ServiceProducerApplicationTests {

    @Test
    void contextLoads() {
    }

}
