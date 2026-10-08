package com.wkedong.springcloud.security.security;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.wkedong.springcloud.security.web.ApiResponse;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 安全链路的 JSON 输出工具。
 * <p>
 * 为什么需要它：认证/授权失败发生在**过滤器链**里，此时还没有进入 DispatcherServlet，
 * {@code @RestControllerAdvice} 完全没机会介入。所以 EntryPoint / AccessDeniedHandler
 * 只能自己往 {@code HttpServletResponse} 里写字节——这个类就是把这段重复代码收拢到一处。
 * <p>
 * 两个细节：
 * <ol>
 *   <li>必须先设置状态码与 Content-Type，再写 body；Content-Type 要带 charset，
 *       否则中文提示在部分客户端会乱码；</li>
 *   <li>用 {@code response.getWriter()} 而不是 OutputStream，配合 UTF-8 charset 最省事。</li>
 * </ol>
 *
 * @author wkedong
 */
public final class AuthErrorWriter {

    /** Jackson 3（Boot 4 默认）：包名从 com.fasterxml.jackson 变为 tools.jackson；
     *  JsonMapper 是不可变构建器风格，线程安全，适合做静态单例 */
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

    private AuthErrorWriter() {
    }

    /**
     * 写出统一结构的错误响应。
     *
     * @param response HTTP 响应
     * @param status   HTTP 状态码（401 未认证 / 403 无权限）
     * @param message  给调用方看的中文提示（不要带内部细节，如「用户不存在」「签名不匹配」）
     * @throws IOException 写响应失败
     */
    public static void write(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiResponse<Void> body = ApiResponse.fail(status, message);
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(body));
        response.getWriter().flush();
    }
}
