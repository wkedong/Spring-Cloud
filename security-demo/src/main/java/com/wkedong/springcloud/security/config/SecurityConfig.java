package com.wkedong.springcloud.security.config;

import com.wkedong.springcloud.security.gateway.GatewayAuthProperties;
import com.wkedong.springcloud.security.security.RestAccessDeniedHandler;
import com.wkedong.springcloud.security.security.RestAuthenticationEntryPoint;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 主配置（Spring Security 5.7 起的**组件式配置**）。
 * <p>
 * <b>为什么不用 WebSecurityConfigurerAdapter</b>：那个类在 5.7 被标记废弃（Boot 2.7 使用 5.7/5.8），
 * 原因是「继承 + 覆写」的组合方式太僵化：一个应用只能有一个 Adapter 承担整条链，
 * 想拆成多条链、想按条件组装，就得靠内部类与 {@code @Order} 绕。组件式配置把「过滤链」变成
 * 一个普通的 Bean，天然支持多 Bean、多链、按 profile 组装。
 * <p>
 * 本类里最值得逐行看的是 {@link #securityFilterChain} 上的注释。
 * <p>
 * 方法级鉴权开关写在启动类 {@code SecurityDemoApplication} 上：
 * 5.7 是 {@code @EnableGlobalMethodSecurity(prePostEnabled = true)}，
 * Spring Security 6 起更名 {@code @EnableMethodSecurity}，
 * <b>7.0 直接移除了旧注解</b>（启动即失败，见启动类的升级注释）。
 *
 * @author wkedong
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(GatewayAuthProperties.class)
public class SecurityConfig {

    /**
     * 密码编码器：BCrypt。
     * <p>
     * 三条铁律：
     * <ol>
     *   <li>数据库里**只能**存哈希，永远不存明文、不存可逆加密（可逆 = 泄露即明文）；</li>
     *   <li>BCrypt 自带随机盐（盐存在哈希串里），所以同一个密码两次编码结果不同，彩虹表无效；</li>
     *   <li>它故意「慢」（可调 strength），把暴力破解成本抬高——这正是它比 MD5/SHA1 更合适的原因。</li>
     * </ol>
     * 注意：{@code matches(明文, 哈希)} 是在登录时逐次计算的，不像摘要算法可以直接比对字符串。
     *
     * @return BCrypt 编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 用户来源：教学用内存用户；生产应换成「JdbcUserDetailsManager + 用户表」或自研 UserDetailsService（见文档思考点）。
     * <p>
     * 这里刻意存**编码后**的密码：把 {@code passwordEncoder.encode("admin123")} 的结果交给 UserDetails，
     * 让认证流程和真实项目完全一致（DaoAuthenticationProvider 用 matches 比对，而不是 equals）。
     *
     * @param passwordEncoder 密码编码器
     * @return 用户明细服务
     */
    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        UserDetails admin = User.withUsername("admin")
                .password(passwordEncoder.encode("admin123"))
                // roles("ADMIN","USER") 等价于权限 ROLE_ADMIN、ROLE_USER
                .roles("ADMIN", "USER")
                .build();
        UserDetails normalUser = User.withUsername("user")
                .password(passwordEncoder.encode("user123"))
                .roles("USER")
                .build();
        return new InMemoryUserDetailsManager(admin, normalUser);
    }

    /**
     * 暴露 AuthenticationManager 给登录接口用。
     * <p>
     * 为什么不自己在 Controller 里 {@code passwordEncoder.matches(...)}：那样会绕过 Spring Security 的
     * 认证流程（账号锁定/过期/是否启用、认证事件、密码升级策略、Provider 链统统失效）。
     * 走 {@code authenticationManager.authenticate()} 才是「一套流程、一个出口」。
     *
     * @param configuration Boot 组装的认证配置
     * @return 认证管理器
     * @throws Exception 构建失败
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /**
     * JWT → 权限的转换器：<b>本模块最容易踩的坑就这一个</b>。
     * <p>
     * Spring Security 的默认实现只认 OAuth2 标准的 {@code scope}/{@code scp} claim，
     * 并且会给权限加上 {@code SCOPE_} 前缀。而我们的令牌里放的是 {@code roles: ["ADMIN","USER"]}，
     * 默认实现读不到任何角色 → 请求「能通过认证」（令牌本身有效）却「过不了授权」
     * （{@code hasRole('ADMIN')} 找不到 {@code ROLE_ADMIN}）→ 管理员接口稳定 403。
     * <p>
     * 修法就是下面三行：改 claim 名 + 补 {@code ROLE_} 前缀。
     * 把 {@code security.jwt.use-default-converter} 设为 true 可以实测这个坑（见文档「关键机制」）。
     *
     * @param properties JWT 配置
     * @return 定制后的转换器
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter(JwtProperties properties) {
        if (properties.isUseDefaultConverter()) {
            // 教学对比开关：故意返回「未定制」的转换器（默认只映射 scope → SCOPE_）
            return new JwtAuthenticationConverter();
        }
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        // 从 roles claim 里取角色
        authoritiesConverter.setAuthoritiesClaimName(properties.getAuthoritiesClaim());
        // 补前缀，否则 hasRole('ADMIN') 比对不上（hasRole 会自己加 ROLE_ 再比较）
        authoritiesConverter.setAuthorityPrefix(properties.getAuthorityPrefix());

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }

    /**
     * 唯一的安全过滤链：把「认证方式、会话策略、放行规则、异常输出、资源服务器」一次讲清。
     *
     * @param http                   HttpSecurity 构建器
     * @param jwtAuthenticationConverter JWT 权限映射器
     * @param entryPointProvider     401 处理器（可能因开关关闭而缺席）
     * @param deniedHandlerProvider  403 处理器（可能因开关关闭而缺席）
     * @return 安全过滤链
     * @throws Exception 构建失败
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationConverter jwtAuthenticationConverter,
                                                   ObjectProvider<RestAuthenticationEntryPoint> entryPointProvider,
                                                   ObjectProvider<RestAccessDeniedHandler> deniedHandlerProvider) throws Exception {
        RestAuthenticationEntryPoint entryPoint = entryPointProvider.getIfAvailable();
        RestAccessDeniedHandler deniedHandler = deniedHandlerProvider.getIfAvailable();

        http
                // ---------- 1) 关 CSRF ----------
                // CSRF 攻击能成立的前提是「浏览器会自动带上凭证」（Cookie/Session）。
                // 纯 Token 方案里凭证放在 Authorization 头，是前端代码显式添加的，
                // 攻击者的站点无法读取/伪造它，所以 CSRF 防护没有用武之地，关掉即可。
                // 反过来：只要还用 Cookie/Session 认证，就**绝对不能**关。
                .csrf(csrf -> csrf.disable())

                // ---------- 2) 关掉表单登录与 HTTP Basic ----------
                // 这两个都会往浏览器弹框/跳登录页，是「面向浏览器」的交互方式；
                // 无状态 API 只要一个 JSON 登录接口（/auth/login），其余交互交给前端。
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())

                // ---------- 3) 会话策略：无状态 ----------
                // STATELESS = 不创建、不使用 HttpSession。
                // 为什么：微服务里同一个用户的请求会被负载均衡打到不同实例，
                // 一旦依赖 Session 就必须做「会话粘滞」或「Session 共享（Redis）」，
                // 既增加基础设施又有单点；而 JWT 自带身份，天然不需要服务端会话。
                // 顺带好处：实例可以随意重启/扩缩容，会话不丢。
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 关闭 logout 端点：无状态服务没有服务端会话可注销，
                // 「退出登录」= 客户端自己删掉令牌（真正的即时失效方案见文档思考点）。
                .logout(logout -> logout.disable())

                // ---------- 4) 授权规则：从上到下第一条命中即生效 ----------
                .authorizeHttpRequests(auth -> auth
                        // 登录接口必须放行，否则「没令牌 → 登录 → 拿令牌」死循环
                        .requestMatchers("/auth/**", "/actuator/health", "/actuator/info").permitAll()
                        // 公开只读接口放行（只放 GET，写操作仍需认证）
                        .requestMatchers(HttpMethod.GET, "/api/public/**").permitAll()
                        // 管理员区：URL 级第一道闸（hasRole('ADMIN') 实际比对权限 ROLE_ADMIN）
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // 其余一律要求「已认证」，具体细粒度再交给方法级 @PreAuthorize
                        .anyRequest().authenticated())

                // ---------- 5) 统一异常输出（401 / 403） ----------
                .exceptionHandling(ex -> {
                    if (entryPoint != null) {
                        ex.authenticationEntryPoint(entryPoint);
                    }
                    if (deniedHandler != null) {
                        ex.accessDeniedHandler(deniedHandler);
                    }
                })

                // ---------- 6) 开启资源服务器：带 Bearer 令牌的请求走 JWT 校验 ----------
                // 它会注册 BearerTokenAuthenticationFilter：
                // 解析 Authorization: Bearer xxx → 用 JwtDecoder 验签/校验 → 得到 JwtAuthenticationToken。
                // 校验失败不会进 Controller，而是回到这里配置的 entry point（这就是 401 的来源）。
                .oauth2ResourceServer(oauth2 -> {
                    // 资源服务器有自己的一套错误出口，必须单独设置，否则仍是默认的无 body 401
                    if (entryPoint != null) {
                        oauth2.authenticationEntryPoint(entryPoint);
                    }
                    oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter));
                });

        return http.build();
    }
}
