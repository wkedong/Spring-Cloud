package com.wkedong.springcloud.serviceproducer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * 配置动态刷新演示 bean。
 * <p>
 * {@code @RefreshScope} 的语义：bean 会被放进 refresh 作用域，调用
 * {@code POST /actuator/refresh}（或配置中心推送刷新）时被**销毁并在下次使用时重建**，
 * 于是 {@code @Value} 注入的值会重新解析 —— 无需重启进程。
 * <p>
 * 对照实验：{@code ProducerServiceImpl} 里同样用 {@code @Value("${name:unknown}")} 注入，
 * 但那个 bean 不是 refresh 作用域，所以刷新后它的值不变。这正是「配置热更新到底更新了什么」的答案：
 * <b>热更新的是被标记为 refresh 作用域的 bean，不是所有 bean</b>。
 *
 * @author wkedong
 */
@Component
@RefreshScope
public class RefreshableConfig {

    /** 来自配置中心（config 服务的 PROPERTIES 表，key=name） */
    @Value("${name:unknown}")
    private String name;

    /** 本地 bootstrap 配置（不属于配置中心，用于对照） */
    @Value("${spring.application.name:unknown}")
    private String applicationName;

    public String getName() {
        return name;
    }

    public String getScopeInfo() {
        return "bean=" + getClass().getSimpleName() + ", applicationName=" + applicationName;
    }
}
