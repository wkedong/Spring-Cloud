package com.wkedong.springcloud.nacos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 「普通 bean + {@code @Value}」的反面样本：配置变了它也不会变。
 * <p>
 * 原因：这个 bean 是单例（singleton scope），启动时解析一次占位符就固定了；
 * 没有 {@code @RefreshScope} 就没有「销毁并重建」的动作，
 * 刷新事件到达时它只是安静地待在容器里。
 * <p>
 * 教学价值：线上最常见的「配置改了没生效」就是这一类——不是 Nacos 没推，
 * 而是推了但这个 bean 不参与刷新。用本模块的
 * {@code GET /nacos/config/preview} 可以把四个探针的差异一次看全。
 *
 * @author wkedong
 */
@Component
public class FrozenValueProbe {

    /** 与 {@link RefreshValueProbe#getTitle()} 同 key，差异只在注解 */
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
