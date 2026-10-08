package com.wkedong.springcloud.nacos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * Nacos 配置的「类型安全」载体：{@code @ConfigurationProperties} + {@code @RefreshScope}。
 * <p>
 * 与 {@code @Value} 相比，它有三个好处：
 * <ol>
 *   <li>批量绑定、支持 int/boolean/List 自动转换（Nacos 里存的都是字符串）；</li>
 *   <li>IDE 里能补全、拼错 key 不会静默变成 null 之外的怪异值；</li>
 *   <li>需要刷新时语义清晰：加了 {@code @RefreshScope} 就是「这个 bean 随配置重建」。</li>
 * </ol>
 * 对照组是 {@link PlainProperties}（同样绑定前缀，但没有 {@code @RefreshScope}），
 * 用 {@code GET /nacos/config/preview} 可以一眼看出两者刷不刷新的差别。
 *
 * @author wkedong
 */
@Component
@RefreshScope
@ConfigurationProperties(prefix = "nacos.demo")
public class DemoProperties {

    /** 标题：三个 dataId 里都写了同名 key，用来观察覆盖顺序 */
    private String title;
    /** 故意与本地 application.yml 同名：Nacos 远端 vs 本地 谁赢 */
    private String priority;
    /** 只写在 nacos-demo-dev.properties（profile dataId）里 */
    private String profileValue;
    /** 只写在 nacos-demo-shared.properties（shared-configs）里 */
    private String sharedValue;
    /** 只写在 nacos-demo.properties（默认 dataId）里 */
    private String remoteOnly;
    /** 布尔型：演示字符串 → boolean 的自动转换 */
    private boolean featureFlag;
    /** 整型：演示字符串 → int 的自动转换 */
    private int maxBatchSize;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getProfileValue() {
        return profileValue;
    }

    public void setProfileValue(String profileValue) {
        this.profileValue = profileValue;
    }

    public String getSharedValue() {
        return sharedValue;
    }

    public void setSharedValue(String sharedValue) {
        this.sharedValue = sharedValue;
    }

    public String getRemoteOnly() {
        return remoteOnly;
    }

    public void setRemoteOnly(String remoteOnly) {
        this.remoteOnly = remoteOnly;
    }

    public boolean isFeatureFlag() {
        return featureFlag;
    }

    public void setFeatureFlag(boolean featureFlag) {
        this.featureFlag = featureFlag;
    }

    public int getMaxBatchSize() {
        return maxBatchSize;
    }

    public void setMaxBatchSize(int maxBatchSize) {
        this.maxBatchSize = maxBatchSize;
    }
}
