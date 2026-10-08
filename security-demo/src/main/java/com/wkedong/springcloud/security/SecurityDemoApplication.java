package com.wkedong.springcloud.security;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;

/**
 * Spring Security + JWT 教学模块入口（端口 8240）。
 * <p>
 * 覆盖内容：
 * <ul>
 *   <li>SecurityFilterChain 配置（Boot 2.7 / Security 5.7 起用组件式配置，取代 WebSecurityConfigurerAdapter）</li>
 *   <li>JWT 签发（NimbusJwtEncoder）与校验（资源服务器 JwtDecoder）</li>
 *   <li>角色/权限控制：{@code @PreAuthorize("hasRole('ADMIN')")} 方法级鉴权</li>
 *   <li>网关侧统一鉴权（把校验逻辑放到网关，业务服务只信任内网请求）</li>
 * </ul>
 *
 * @author wkedong
 */
@SpringBootApplication
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecurityDemoApplication.class, args);
    }
}
