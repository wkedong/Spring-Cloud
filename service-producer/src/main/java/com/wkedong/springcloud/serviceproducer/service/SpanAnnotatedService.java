package com.wkedong.springcloud.serviceproducer.service;

import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 注解式自定义 span（Micrometer Observation 的 {@code @Observed}）。
 * <p>
 * 与编程式（{@code tracer.nextSpan()}）的分工：
 * <ul>
 *   <li>注解式：代码最干净，适合「进入某个方法就算一个步骤」的场景；</li>
 *   <li>编程式：可以动态命名、条件性创建、手动控制结束时机，还能带上下文的动态标签。</li>
 * </ul>
 * 迁移要点（Sleuth → Micrometer Tracing）：
 * <ul>
 *   <li>Sleuth 的 {@code @NewSpan}/{@code @SpanTag} 已随 Sleuth 一起退役，替代品是 Observation API 的
 *       {@code @Observed}；它需要 AOP 生效，Boot 侧开关是
 *       {@code management.observations.annotations.enabled=true}（开启后自动注册 ObservedAspect）。</li>
 *   <li>{@code @Observed} 只支持**常量**低基数标签（写死在注解里）；想按参数打标签必须用编程式
 *       （{@code Observation.createNotStarted(...).lowCardinalityKeyValue("demo.arg", arg)}）——
 *       这是「注解能力有限、编程式兜底」的真实分界线。</li>
 *   <li>{@code @Observed} 同样依赖 AOP 代理，<b>同类内部自调用不生效</b>（与 @Transactional/@Cacheable 同源问题），
 *       所以本类被单独抽出来，由 Controller 直接注入调用。</li>
 * </ul>
 *
 * @author wkedong
 */
@Service
public class SpanAnnotatedService {

    private static final Logger logger = LoggerFactory.getLogger(SpanAnnotatedService.class);

    /** 注解式 span：name 即 Zipkin 里看到的 span 名 */
    @Observed(name = "producer-annotated-span", contextualName = "annotated-work")
    public void annotatedWork(String arg) {
        logger.info("注解式 span 内：arg={}", arg);
        try {
            Thread.sleep(60L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
