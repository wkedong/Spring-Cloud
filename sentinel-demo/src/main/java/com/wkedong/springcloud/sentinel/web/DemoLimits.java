package com.wkedong.springcloud.sentinel.web;

/**
 * 演示接口的入参边界（信任边界上的「上限」）。
 * <p>
 * 为什么要单独抽一个类：几个接口都有 {@code sleepMs} 这种「让本次请求慢一点」的参数，
 * 如果不设上限，调用方传一个 {@code sleepMs=999999999} 就能把 Tomcat 的工作线程长期占住——
 * 这是典型的**资源耗尽型风险**（一个参数就能拖垮整个服务），而不是「演示代码无所谓」。
 * 注解里只能用编译期常量，所以这些值必须定义成 {@code public static final}。
 */
public final class DemoLimits {

    /** 单次请求允许的最大「模拟耗时」：2000ms（够覆盖读超时 1000ms 与熔断阈值 500ms 的演示） */
    public static final long MAX_SLEEP_MS = 2000L;

    /** 最小 0ms：允许演示「完全不小睡」的对照场景 */
    public static final long MIN_SLEEP_MS = 0L;

    /** 热点参数值的最大长度：防止超长字符串把统计维度撑爆（也避免日志被灌） */
    public static final int MAX_PARAM_LENGTH = 64;

    private DemoLimits() {
    }
}
