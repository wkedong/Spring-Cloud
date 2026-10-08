package com.wkedong.springboot.basics.validation;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义校验注解示例：中国大陆手机号。
 * <p>
 * 三个必备要素（教学点）：
 * <ol>
 *   <li>{@code @Constraint(validatedBy = ...)}：指定真正的校验逻辑；</li>
 *   <li>{@code message()}：必须提供，且通常给默认值；配合 ValidationMessages.properties 可国际化；</li>
 *   <li>{@code groups()} 与 {@code payload()}：Bean Validation 规范要求必须声明这两个方法。</li>
 * </ol>
 *
 * @author wkedong
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ChineseMobileValidator.class)
public @interface ChineseMobile {

    String message() default "手机号格式不正确（示例：13800138000）";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** 是否允许为空（false 时由 @NotBlank 等注解负责必填校验，避免职责重叠） */
    boolean allowEmpty() default false;
}
