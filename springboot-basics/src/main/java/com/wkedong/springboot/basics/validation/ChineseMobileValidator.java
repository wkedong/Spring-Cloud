package com.wkedong.springboot.basics.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/**
 * {@link ChineseMobile} 的校验逻辑。
 * <p>
 * 注意：校验器方法应当**无状态且快速**——它会在每次参数绑定被调用，
 * 复杂校验（查数据库、调远程）应放到 Service 层。
 *
 * @author wkedong
 */
public class ChineseMobileValidator implements ConstraintValidator<ChineseMobile, String> {

    private static final Pattern MOBILE = Pattern.compile("^1[3-9]\\d{9}$");

    private boolean allowEmpty;

    @Override
    public void initialize(ChineseMobile annotation) {
        this.allowEmpty = annotation.allowEmpty();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.trim().isEmpty()) {
            return allowEmpty;
        }
        return MOBILE.matcher(value).matches();
    }
}
