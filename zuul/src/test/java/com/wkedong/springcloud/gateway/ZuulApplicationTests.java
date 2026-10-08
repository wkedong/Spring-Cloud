package com.wkedong.springcloud.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Boot 2.4+ 的 spring-boot-starter-test 只带 JUnit 5（Jupiter），
 * 原 JUnit 4 的 @RunWith(SpringRunner.class) 写法一并移除。
 */
@SpringBootTest
class ZuulApplicationTests {

    @Test
    void contextLoads() {
    }

}
