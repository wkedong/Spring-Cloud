package com.wkedong.springcloud.nacos.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * 负载均衡调用所需的 RestTemplate。
 * <p>
 * {@code @LoadBalanced} 做的事：给 RestTemplate 装一个拦截器，把
 * {@code http://nacos-demo/xxx} 里的 {@code nacos-demo} 先解析成注册中心的
 * 一个真实实例（host:port），再发请求——所以代码里永远不写死 IP。
 * <p>
 * 2021.0.x 的默认实现是 Spring Cloud LoadBalancer（Ribbon 已退役），
 * 默认策略 {@code RoundRobinLoadBalancer}，即轮询。
 * <p>
 * 注意：带 {@code @LoadBalanced} 的 RestTemplate 只能用于「服务名」调用；
 * 想直连某个 IP（例如被 lb-call 调用的自述端点内部实现）就用普通的 new。
 *
 * @author wkedong
 */
@Configuration
public class RestTemplateConfig {

    /**
     * 按服务名调用的模板：调用方只写 {@code http://nacos-demo/...}。
     */
    @Bean
    @LoadBalanced
    public RestTemplate loadBalancedRestTemplate() {
        return new RestTemplate();
    }
}
