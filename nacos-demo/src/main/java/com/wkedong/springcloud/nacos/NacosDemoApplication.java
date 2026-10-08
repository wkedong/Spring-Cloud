package com.wkedong.springcloud.nacos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Nacos 教学模块入口（端口 8210）。
 * <p>
 * 演示三件事：
 * <ol>
 *   <li><b>服务注册发现</b>：启动后可在 Nacos 控制台（http://127.0.0.1:8848/nacos/）看到实例；</li>
 *   <li><b>配置中心</b>：配置从 Nacos 拉取（bootstrap.yml 里声明 dataId/group），改配置无需重启；</li>
 *   <li><b>动态刷新</b>：{@code @RefreshScope} + Nacos 配置变更，值实时生效。</li>
 * </ol>
 * 与 Eureka/Config 的对照见 docs/24-Nacos注册与配置.md。
 *
 * @author wkedong
 */
@SpringBootApplication
@EnableDiscoveryClient
public class NacosDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(NacosDemoApplication.class, args);
    }
}
