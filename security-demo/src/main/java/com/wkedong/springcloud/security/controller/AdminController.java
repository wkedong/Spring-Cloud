package com.wkedong.springcloud.security.controller;

import com.wkedong.springcloud.security.web.ApiResponse;
import com.wkedong.springcloud.security.web.dto.ConfigUpdateRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员接口：读用户列表 + 写配置，**读写都要 ADMIN**。
 * <p>
 * 这里演示「两道闸」的写法（生产建议都留）：
 * <ol>
 *   <li><b>URL 级</b>：{@code .antMatchers("/api/admin/**").hasRole("ADMIN")}——
 *       在过滤器链里就拦掉，成本最低，也最不容易漏；</li>
 *   <li><b>方法级</b>：{@code @PreAuthorize("hasRole('ADMIN')")}——
 *       跟着方法走，即使将来有人改了路由/加了新入口，权限也不会跟着丢。</li>
 * </ol>
 * 只写 URL 级的问题：新增一个 {@code /api/manage/**} 就漏了；只写方法级的风险：
 * 忘记加注解的接口就成了裸接口。两个都写才是「默认拒绝」。
 *
 * @author wkedong
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    /**
     * 用户列表（管理员可见）。
     * <p>
     * URL 级规则已要求 ADMIN，方法级再来一次，形成纵深防御。
     *
     * @return 演示用用户列表
     */
    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<Map<String, Object>>> users() {
        List<Map<String, Object>> users = new ArrayList<>();
        users.add(row("admin", "ADMIN,USER", "启用"));
        users.add(row("user", "USER", "启用"));
        return ApiResponse.ok(users);
    }

    /**
     * 修改配置（管理员写操作）。
     * <p>
     * 写操作比读操作更危险，除了角色校验，真实系统还应叠加：审计日志、二次确认、
     * 变更审批、灰度发布——权限只解决「谁能点」，不解决「点了出问题怎么办」。
     *
     * @param request 配置项
     * @param authentication 当前认证信息（用于审计「谁改的」）
     * @return 变更回执
     */
    @PostMapping("/config")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> updateConfig(@Valid @RequestBody ConfigUpdateRequest request,
                                                         Authentication authentication) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("key", request.getKey());
        data.put("value", request.getValue());
        data.put("applied", Boolean.TRUE);
        data.put("operator", authentication.getName());
        data.put("operatedAt", Instant.now().toString());
        return ApiResponse.ok(data);
    }

    private Map<String, Object> row(String username, String roles, String status) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("username", username);
        row.put("roles", roles);
        row.put("status", status);
        return row;
    }
}
