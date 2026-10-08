package com.wkedong.springboot.basics.web;

import com.wkedong.springboot.basics.config.BasicsProperties;
import com.wkedong.springboot.basics.config.CacheConfig;
import com.wkedong.springboot.basics.domain.User;
import com.wkedong.springboot.basics.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web 层切片测试：{@code @WebMvcTest} 只加载 MVC 相关组件（Controller、@ControllerAdvice、
 * Filter、HandlerInterceptor、WebMvcConfigurer），**不启动数据库、不连注册中心**，因此毫秒级完成。
 * <p>
 * 教学点：
 * <ul>
 *   <li>依赖用 {@code @MockitoBean} 替换（Boot 3.4 起 {@code @MockBean} 已废弃、Boot 4 已删除），
 *       测试只关心「请求进 → 响应出」的行为；</li>
 *   <li>MockMvc 不经过真实网络，但会走完整的 Filter → Interceptor → ControllerAdvice 链路，
 *       所以能验证鉴权与统一异常处理；</li>
 *   <li>本模块的 BasicsProperties 不在切片范围内，需要显式 {@code @EnableConfigurationProperties} 引入。</li>
 * </ul>
 *
 * 升级要点（Boot 4 / Framework 7）：
 * <ol>
 *   <li>{@code @WebMvcTest} 换了包名（{@code org.springframework.boot.webmvc.test.autoconfigure}），
 *       它所在的 spring-boot-webmvc-test 模块也不再被 spring-boot-starter-test 传递依赖，需显式声明；</li>
 *   <li>{@code @MockBean} 已删除，改用 {@code @MockitoBean}；</li>
 *   <li>缓存切面在 Framework 7 里启动即校验 CacheManager，切片测试必须 {@code @Import} 真实的缓存配置
 *       （旧版本是延迟到首次调用才报错，所以这个坑以前看不出来）。</li>
 * </ol>
 *
 * @author wkedong
 */
@WebMvcTest(controllers = {UserController.class, DemoController.class})
@EnableConfigurationProperties(BasicsProperties.class)
// 切片不加载 @Configuration（WebMvcTypeExcludeFilter 会滤掉 CacheConfig），
// 而主类上的 @EnableCaching 让缓存切面必须在启动时就拿到 CacheManager：
// Spring Framework 7 起这里由「延迟报错」变成「启动即失败」，切片测试要把缺失的基础设施显式补上。
@Import(CacheConfig.class)
class UserControllerTest {

    private static final String TOKEN_HEADER = "X-Token";
    private static final String TOKEN = "dev-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    void 未带token返回401与统一响应体() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100))
                .andExpect(jsonPath("$.message").value("未认证：请在请求头携带 X-Token"));
    }

    @Test
    void 白名单接口无需token() throws Exception {
        mockMvc.perform(get("/api/public/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.pong").value(true));
    }

    @Test
    void 参数校验失败返回字段级错误() throws Exception {
        String body = "{\"name\":\"\",\"email\":\"not-an-email\",\"phone\":\"12345\",\"age\":0}";
        mockMvc.perform(post("/api/users")
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.data.name").value("姓名不能为空"))
                .andExpect(jsonPath("$.data.email").value("邮箱格式不正确"))
                .andExpect(jsonPath("$.data.phone").value("手机号格式不正确"))
                .andExpect(jsonPath("$.data.age").value("年龄必须大于 0"));
    }

    @Test
    void 校验通过时返回统一成功结构() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setName("王五");
        given(userService.create(any())).willReturn(user);

        String body = "{\"name\":\"王五\",\"email\":\"wangwu@example.com\",\"phone\":\"13800138000\",\"age\":30}";
        mockMvc.perform(post("/api/users")
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("王五"))
                // traceId 由 TraceIdFilter 写入 MDC 并带进响应体，便于日志追踪
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void 系统异常返回固定文案不泄露堆栈() throws Exception {
        mockMvc.perform(get("/api/demo/system-error").header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(50001))
                .andExpect(jsonPath("$.message").value("服务器内部错误，请联系管理员并提供 traceId"));
    }
}
