package com.wkedong.springboot.basics.audit;

import com.wkedong.boot.audit.AuditLogger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 审计切面：把 {@link AuditLog} 标注的方法调用交给 starter 里的 {@link AuditLogger} 记录。
 * <p>
 * 这一个小类同时串起了四个知识点：
 * <ol>
 *   <li><b>自定义 starter</b>：AuditLogger 是自动配置注入进来的，本模块没有 new 过它；</li>
 *   <li><b>AOP</b>：{@code @Around} 环绕通知能拿到入参、返回值、异常与耗时；</li>
 *   <li><b>MDC</b>：把 traceId 一起写进审计记录，便于和访问日志对齐；</li>
 *   <li><b>注解驱动</b>：业务代码只加一行 @AuditLog，横切逻辑零侵入。</li>
 * </ol>
 * 生产提示：审计日志通常要落库/投递 MQ 且不可篡改，演示里只放内存环形缓冲。
 *
 * @author wkedong
 */
@Aspect
@Component
public class AuditAspect {

    private final AuditLogger auditLogger;

    public AuditAspect(AuditLogger auditLogger) {
        this.auditLogger = auditLogger;
    }

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        long start = System.currentTimeMillis();
        String detail = auditLog.withArgs()
                ? joinPoint.getSignature().toShortString() + " args=" + Arrays.toString(joinPoint.getArgs())
                : joinPoint.getSignature().toShortString();
        String traceId = MDC.get("traceId");
        if (traceId != null) {
            detail = detail + " traceId=" + traceId;
        }
        try {
            Object result = joinPoint.proceed();
            auditLogger.record(auditLog.value(), detail + " 成功", System.currentTimeMillis() - start);
            return result;
        } catch (Throwable ex) {
            auditLogger.record(auditLog.value(), detail + " 失败：" + ex.getMessage(),
                    System.currentTimeMillis() - start);
            throw ex;
        }
    }
}
