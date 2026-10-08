package com.wkedong.springboot.basics.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要审计的方法（AOP + 自定义 starter 的组合演示）。
 *
 * @author wkedong
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    /** 操作名（必填），会出现在审计日志里 */
    String value();

    /** 是否记录方法参数（参数可能含敏感信息，默认不记） */
    boolean withArgs() default false;
}
