package com.wkedong.springboot.basics.web;

import com.wkedong.springboot.basics.audit.AuditLog;
import com.wkedong.springboot.basics.domain.User;
import com.wkedong.springboot.basics.service.UserService;
import com.wkedong.springboot.basics.web.dto.UserCreateRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;

/**
 * 用户接口：统一响应 + 参数校验 + 缓存 + 审计的综合示例。
 * <p>
 * 注意所有接口都在 {@code /api/**} 下：拦截器会校验请求头 {@code X-Token: dev-token}
 * （演示用，见 {@code basics.security-token} 配置）。
 * <p>
 * 类上的 {@code @Validated} 是方法参数级校验（如 @PathVariable @Min(1)）生效的前提，
 * 没有它，这些注解会被静默忽略——这也是常见坑之一。
 *
 * @author wkedong
 */
@RestController
@RequestMapping("/api/users")
@Validated
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<List<User>> list() {
        return ApiResponse.ok(userService.list());
    }

    /** 走 users 缓存的查询 */
    @GetMapping("/{id}")
    public ApiResponse<User> get(@PathVariable("id") @Min(1) Long id) {
        return ApiResponse.ok(userService.getById(id));
    }

    /** @Valid 触发 JSR-303 校验；@AuditLog 触发切面记录审计（组合演示） */
    @PostMapping
    @AuditLog("创建用户")
    public ApiResponse<User> create(@Valid @RequestBody UserCreateRequest request) {
        return ApiResponse.ok(userService.create(request));
    }

    @PutMapping("/{id}")
    @AuditLog("重命名用户")
    public ApiResponse<User> rename(@PathVariable("id") Long id, @RequestParam("name") String name) {
        return ApiResponse.ok(userService.rename(id, name));
    }

    @DeleteMapping("/{id}")
    @AuditLog("删除用户")
    public ApiResponse<String> delete(@PathVariable("id") Long id) {
        userService.delete(id);
        return ApiResponse.ok("deleted");
    }
}
