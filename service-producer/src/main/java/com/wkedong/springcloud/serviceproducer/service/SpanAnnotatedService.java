package com.wkedong.springcloud.serviceproducer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.sleuth.annotation.NewSpan;
import org.springframework.cloud.sleuth.annotation.SpanTag;
import org.springframework.stereotype.Service;

/**
 * 注解式自定义 span（Sleuth 3.x {@code @NewSpan}）。
 * <p>
 * 与编程式（{@code tracer.nextSpan()}）的分工：
 * <ul>
 *   <li>注解式：代码最干净，适合「进入某个方法就算一个步骤」的场景；</li>
 *   <li>编程式：可以动态命名、条件性创建、手动控制结束时机。</li>
 * </ul>
 * 注意：{@code @NewSpan} 依赖 AOP 代理，**同类内部自调用不生效**（与 @Transactional/@Cacheable 同源问题），
 * 所以本类被单独抽出来，由 Controller 直接注入调用。
 *
 * @author wkedong
 */
@Service
public class SpanAnnotatedService {

    private static final Logger logger = LoggerFactory.getLogger(SpanAnnotatedService.class);

    /** 注解里可以用 SpEL 引用参数值命名 span */
    @NewSpan("producer-annotated-span")
    public void annotatedWork(@SpanTag("demo.arg") String arg) {
        logger.info("注解式 span 内：arg={}", arg);
        try {
            Thread.sleep(60L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
