package com.wkedong.springcloud.nacos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 「不加 {@code @RefreshScope} 的 {@code @ConfigurationProperties}」对照 bean。
 * <p>
 * 存在的唯一目的：把网上流传的说法（「Nacos 下 {@code @ConfigurationProperties}
 * 必须加 {@code @RefreshScope} 才刷新」）拿到本机实测一遍。
 * 它与 {@link DemoProperties} 绑定同一个前缀、字段完全一致，
 * 差别只有 {@code @RefreshScope} 一个注解，因此
 * {@code GET /nacos/config/preview} 的输出就是结论本身。
 *
 * @author wkedong
 */
@Component
@ConfigurationProperties(prefix = "nacos.demo")
public class PlainProperties {

    private String title;
    private String priority;
    private String profileValue;
    private String sharedValue;
    private String remoteOnly;
    private boolean featureFlag;
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
