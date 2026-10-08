package com.wkedong.springcloud.nacos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * 「{@code @Value} + {@code @RefreshScope}」的正面样本。
 * <p>
 * 原理：{@code @RefreshScope} 的 bean 实际是个作用域代理，字段是在「实例化那一刻」
 * 通过占位符解析注入的。刷新事件到达时 Spring Cloud 销毁该作用域下的实例，
 * 下次调用 getter 时重新创建 → 重新解析占位符 → 拿到新值。
 * <p>
 * 所以「{@code @Value} 能不能热更新」的答案取决于它所在 bean 有没有
 * {@code @RefreshScope}：有就刷新，没有就永远停在启动时的值
 * （对照组见 {@link FrozenValueProbe}）。
 * <p>
 * 占位符都写了 {@code :} 默认值：配置中心里删掉某个 key 时，应用不应该起不来。
 * <p>
 * 另一个实测坑：{@code @Value} 的 key 必须与配置里的写法逐字一致（本类统一 kebab-case）。
 * 它没有 {@code @ConfigurationProperties} 那种松散绑定，写成 {@code nacos.demo.profileValue}
 * 会静默落到默认值。证据见 {@code GET /nacos/pitfall/checklist} 的「松散绑定」一节。
 *
 * @author wkedong
 */
@Component
@RefreshScope
public class RefreshValueProbe {

    /** 与 DemoProperties.title 同 key，便于并排比较两种绑定方式的刷新结果 */
    @Value("${nacos.demo.title:<未配置>}")
    private String title;

    @Value("${nacos.demo.profile-value:<未配置>}")
    private String profileValue;

    @Value("${nacos.demo.shared-value:<未配置>}")
    private String sharedValue;

    @Value("${nacos.demo.max-batch-size:0}")
    private int maxBatchSize;

    public String getTitle() {
        return title;
    }

    public String getProfileValue() {
        return profileValue;
    }

    public String getSharedValue() {
        return sharedValue;
    }

    public int getMaxBatchSize() {
        return maxBatchSize;
    }
}
