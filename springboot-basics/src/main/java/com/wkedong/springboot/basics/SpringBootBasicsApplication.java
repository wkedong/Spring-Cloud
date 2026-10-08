package com.wkedong.springboot.basics;

import com.wkedong.springboot.basics.config.BasicsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot 本体教学模块入口。
 * <p>
 * 这个模块**不依赖注册中心/配置中心**，单机即可启动（H2 内存库），
 * 用来把 Spring Boot 自身的能力讲清楚：
 * <ul>
 *   <li>Web 层工程化：统一响应、全局异常、参数校验、拦截器/过滤器</li>
 *   <li>配置体系：多 profile、@ConfigurationProperties 绑定与校验、配置优先级</li>
 *   <li>数据访问与事务：JPA、事务传播与「自调用失效」</li>
 *   <li>缓存与并发：Caffeine 缓存、@Async 线程池、@Scheduled</li>
 *   <li>可观测与运维：Actuator 端点、自定义健康检查、优雅停机、动态日志级别</li>
 *   <li>测试：切片测试（@WebMvcTest）与集成测试</li>
 *   <li>自动配置原理：本模块依赖了自带写的 basics-audit starter</li>
 * </ul>
 * 启动：{@code java -jar springboot-basics/target/springboot-basics-0.0.1-SNAPSHOT.jar}（端口 8010）
 *
 * @author wkedong
 */
@SpringBootApplication
@EnableCaching
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(BasicsProperties.class)
public class SpringBootBasicsApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringBootBasicsApplication.class, args);
    }
}
