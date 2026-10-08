package com.wkedong.springcloud.sentinel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Sentinel 教学模块入口（端口 8220）。
 * <p>
 * 演示四类规则（全部用编程式 API 在启动时加载，见 {@code SentinelRuleConfig}）：
 * <ul>
 *   <li>流控规则：QPS 快速失败 / 并发线程数 / 排队等待（RateLimiterController）</li>
 *   <li>熔断降级规则：慢调用比例、异常比例、异常数（三种 grade 各一个资源，互不干扰）</li>
 *   <li>热点参数限流：对某个参数值单独限流，其它参数值不受影响</li>
 *   <li>系统保护规则：Load / RT / 线程数 / 入口 QPS（默认关闭，可运行时打开）</li>
 * </ul>
 * 不依赖注册中心与配置中心：Feign 用 {@code url} 直连本机 8220，
 * 单机即可复现全部实验（见 docs/23-Sentinel流控与降级.md）。
 * <p>
 * 与 Resilience4j 的对照见文档「关键机制」一节：Sentinel 胜在规则动态化与控制台，
 * Resilience4j 胜在纯 Spring 生态、无额外服务端与运维成本。
 *
 * @author wkedong
 */
@SpringBootApplication
@EnableFeignClients
public class SentinelDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(SentinelDemoApplication.class, args);
    }
}
