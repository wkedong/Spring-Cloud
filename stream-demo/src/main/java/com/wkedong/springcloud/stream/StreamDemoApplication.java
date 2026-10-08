package com.wkedong.springcloud.stream;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Cloud Stream 教学模块入口（端口 8230）。
 * <p>
 * 函数式编程模型（Spring Cloud Stream 3.x 起推荐，取代 @EnableBinding/@StreamListener）：
 * <pre>
 * &#64;Bean
 * public Consumer&lt;String&gt; consume() { ... }        // 消费
 * &#64;Bean
 * public Supplier&lt;String&gt; produce() { ... }        // 生产（定时触发）
 * &#64;Bean
 * public Function&lt;String, String&gt; transform() { }  // 处理
 * </pre>
 * binding 名 = 方法名 + -in/-out，通过 spring.cloud.stream.bindings.* 配置目标 topic、
 * 消费者组、分区等，业务代码与中间件解耦（换 RabbitMQ 只改依赖与 binder 配置）。
 *
 * @author wkedong
 */
@SpringBootApplication
public class StreamDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(StreamDemoApplication.class, args);
    }
}
