package com.wkedong.springcloud.security.web.dto;

import javax.validation.constraints.NotBlank;

/**
 * 登录请求体。
 *
 * @author wkedong
 */
public class LoginRequest {

    /** 用户名（对应 UserDetailsService 里的账号） */
    @NotBlank(message = "username 不能为空")
    private String username;

    /** 明文密码：只能走 HTTPS 传输，服务端立即用 BCrypt 比对，绝不落库明文 */
    @NotBlank(message = "password 不能为空")
    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
