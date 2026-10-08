package com.wkedong.springboot.basics.config;

import com.wkedong.springboot.basics.web.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 配置：注册拦截器与跨域。
 * <p>
 * 教学点（Filter 与 Interceptor 的区别，面试高频）：
 * <pre>
 *        Filter（Servlet 规范）          Interceptor（Spring MVC）
 * 归属    Servlet 容器                  Spring 容器
 * 范围    所有请求（含静态资源）         只拦 DispatcherServlet 处理的请求
 * 能否拿到 Bean  仅能拿到容器引用          天然支持依赖注入
 * 能否拿到 controller 方法  不能         能（HandlerMethod，可读注解）
 * 典型用途  编码、请求包装、traceId        鉴权、日志、限流
 * </pre>
 * 本模块两者都演示：{@code TraceIdFilter} 负责请求级追踪号写入 MDC，
 * {@code AuthInterceptor} 负责按路径做接口鉴权。
 *
 * @author wkedong
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebMvcConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/public/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
