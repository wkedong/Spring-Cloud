package com.wkedong.springcloud.serviceconsumer.ribbon.config;

import com.wkedong.springcloud.serviceconsumer.ribbon.service.GrayHeaderLoadBalancer;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.loadbalancer.core.ReactorLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * 自定义负载均衡配置（只作用于声明它的那个客户端）。
 * <p>
 * ⚠️ 两个必须注意的点：
 * <ol>
 *   <li>本类**不加 {@code @Configuration}**：它在组件扫描路径下，加了就会变成全局默认配置，
 *       影响所有负载均衡客户端（Feign 的 configuration 类同理）；</li>
 *   <li>{@code ServiceInstanceListSupplier} 决定「有哪些实例可选」，
 *       {@code ReactorLoadBalancer} 决定「怎么从里面挑一个」——两者要分别定制时改这里。</li>
 * </ol>
 * 官方写法：用 {@code loadBalancerClientFactory.getLazyProvider(name, ServiceInstanceListSupplier.class)}
 * 拿到该客户端的实例供应器（懒加载，避免循环依赖）。
 *
 * @author wkedong
 */
public class GrayLoadBalancerConfiguration {

    /**
     * 实例来源：注册中心（DiscoveryClient）+ 本地缓存（默认缓存 35s，可在 yml 里调小便于观察实例变化）
     * <p>
     * ⚠️ 实测坑：这里必须用 {@code withBlockingDiscoveryClient()}，不能用 {@code withDiscoveryClient()}。
     * 后者需要类路径上存在 {@code ReactiveDiscoveryClient}（WebFlux 栈），而本模块是 servlet 栈 +
     * Eureka 阻塞式客户端，实测会抛：
     * <pre>
     * BeanCreationException: Factory method 'serviceInstanceListSupplier' threw exception;
     * nested exception is NoSuchBeanDefinitionException:
     * No qualifying bean of type 'org.springframework.cloud.client.discovery.ReactiveDiscoveryClient' available
     * </pre>
     * 选择原则：servlet 应用用 blocking，WebFlux/响应式应用用 reactive。
     */
    @Bean
    public ServiceInstanceListSupplier serviceInstanceListSupplier(ConfigurableApplicationContext context) {
        return ServiceInstanceListSupplier.builder()
                .withBlockingDiscoveryClient()
                .withCaching()
                .build(context);
    }

    /** 选择策略：灰度优先，其次轮询 */
    @Bean
    public ReactorLoadBalancer<ServiceInstance> grayHeaderLoadBalancer(Environment environment,
                                                                      LoadBalancerClientFactory loadBalancerClientFactory) {
        String name = environment.getProperty(LoadBalancerClientFactory.PROPERTY_NAME);
        return new GrayHeaderLoadBalancer(
                loadBalancerClientFactory.getLazyProvider(name, ServiceInstanceListSupplier.class), name);
    }
}
