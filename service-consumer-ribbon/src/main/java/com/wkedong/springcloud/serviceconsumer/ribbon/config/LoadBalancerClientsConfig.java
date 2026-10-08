package com.wkedong.springcloud.serviceconsumer.ribbon.config;

import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.context.annotation.Configuration;

/**
 * 把自定义负载均衡策略绑定到具体的服务（客户端）上。
 * <p>
 * 两种粒度：
 * <pre>
 * @LoadBalancerClient(name = "service-producer", configuration = GrayLoadBalancerConfiguration.class)  // 单个服务
 * @LoadBalancerClients(defaultConfiguration = XxxConfiguration.class)                                  // 全局默认
 * </pre>
 * 这里只对 {@code service-producer} 生效，其它服务仍用默认的轮询策略——灰度要精准，不能全站开。
 *
 * @author wkedong
 */
@Configuration
@LoadBalancerClients({
        @LoadBalancerClient(name = "service-producer", configuration = GrayLoadBalancerConfiguration.class)
})
public class LoadBalancerClientsConfig {
}
