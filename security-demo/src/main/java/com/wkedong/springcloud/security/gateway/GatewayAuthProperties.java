package com.wkedong.springcloud.security.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 网关侧鉴权演示配置：{@code security.gateway.*}
 * <p>
 * 这座配置在真实网关模块里就对应「哪些路由要鉴权、哪些路径白名单」——
 * 网关的鉴权规则本质上就是一张「路径 → 要求」的表，写在配置里比写死在代码里更好维护。
 *
 * @author wkedong
 */
@ConfigurationProperties(prefix = "security.gateway")
public class GatewayAuthProperties {

    /** 是否启用网关过滤器（默认关闭：本模块自己已经是资源服务器，过滤器是给网关抄的样例） */
    private boolean enabled = false;

    /** 需要保护的路径（Ant 风格），默认拦下所有 /api/** */
    private List<String> protectedPaths = new ArrayList<>(Arrays.asList("/api/**"));

    /** 免鉴权白名单（登录、公开接口、健康检查） */
    private List<String> whitelist = new ArrayList<>(Arrays.asList("/auth/**", "/api/public/**", "/actuator/**"));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getProtectedPaths() {
        return protectedPaths;
    }

    public void setProtectedPaths(List<String> protectedPaths) {
        this.protectedPaths = protectedPaths;
    }

    public List<String> getWhitelist() {
        return whitelist;
    }

    public void setWhitelist(List<String> whitelist) {
        this.whitelist = whitelist;
    }
}
