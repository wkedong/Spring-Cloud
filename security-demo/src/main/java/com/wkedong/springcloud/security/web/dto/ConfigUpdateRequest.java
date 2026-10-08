package com.wkedong.springcloud.security.web.dto;

import javax.validation.constraints.NotBlank;

/**
 * 管理员写操作（POST /api/admin/config）的请求体。
 *
 * @author wkedong
 */
public class ConfigUpdateRequest {

    /** 配置项名 */
    @NotBlank(message = "key 不能为空")
    private String key;

    /** 配置项值（教学演示：真实系统这里往往还要做二次确认、审计、灰度） */
    @NotBlank(message = "value 不能为空")
    private String value;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
