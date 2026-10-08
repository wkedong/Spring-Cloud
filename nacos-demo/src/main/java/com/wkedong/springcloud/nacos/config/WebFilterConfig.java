package com.wkedong.springcloud.nacos.config;

import com.wkedong.springcloud.nacos.web.TraceIdFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Web 层装配：只把 TraceIdFilter 挂在自己的端点前缀上。
 * <p>
 * 为什么不用 {@code @Component} 让容器自动注册过滤器：那样会连
 * {@code /actuator/**} 一起拦，而 actuator 的指标端点不需要 traceId；
 * 显式声明 urlPatterns 让「哪些请求被追踪」一目了然。
 *
 * @author wkedong
 */
@Configuration
public class WebFilterConfig {

    @Bean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistration() {
        FilterRegistrationBean<TraceIdFilter> registration = new FilterRegistrationBean<TraceIdFilter>();
        registration.setFilter(new TraceIdFilter());
        registration.addUrlPatterns("/nacos/*");
        registration.setName("nacosTraceIdFilter");
        registration.setOrder(1);
        return registration;
    }
}
