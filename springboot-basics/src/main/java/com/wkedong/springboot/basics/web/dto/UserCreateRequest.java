package com.wkedong.springboot.basics.web.dto;

import com.wkedong.springboot.basics.validation.ChineseMobile;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建用户的请求体：参数校验演示。
 * <p>
 * 校验注解速记（常用几个）：
 * <pre>
 * @NotNull      不能为 null
 * @NotBlank     字符串不能为 null/空/纯空白（最常用于文本）
 * @NotEmpty     集合/数组/字符串非空（字符串空白算通过）
 * @Size         长度/大小范围
 * @Min/@Max     数值范围
 * @Email        邮箱格式
 * @Pattern      正则
 * @ChineseMobile 本模块自定义注解
 * </pre>
 * 校验失败会抛 MethodArgumentNotValidException，由 GlobalExceptionHandler 统一翻译成字段级错误。
 *
 * @author wkedong
 */
public class UserCreateRequest {

    @NotBlank(message = "姓名不能为空")
    @Size(max = 20, message = "姓名长度不能超过 20")
    private String name;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @ChineseMobile(message = "手机号格式不正确")
    private String phone;

    @Min(value = 1, message = "年龄必须大于 0")
    @Max(value = 150, message = "年龄不合理")
    private Integer age;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }
}
