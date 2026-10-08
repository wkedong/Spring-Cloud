package com.wkedong.springcloud.nacos.service;

/**
 * Nacos 属性源的名字识别工具。
 * <p>
 * 存在的理由：Nacos 配置在环境里的「长相」取决于加载方式，实测有两种：
 * <ul>
 *   <li>bootstrap 模式：每个 dataId 被单独包装成
 *       {@code bootstrapProperties-<dataId>,<group>}（本模块用的方式）；</li>
 *   <li>config-import 模式：形如 {@code <group>@<dataId>}，且优先级由
 *       {@code spring.config.import} 里 dataId 的书写顺序决定（后写的优先）。</li>
 * </ul>
 * 识别逻辑只写一份，配置预览与踩坑自检都复用它，避免两处判断不一致。
 *
 * @author wkedong
 */
public final class NacosPropertySources {

    /** bootstrap 模式下 Nacos 属性源的名字前缀 */
    public static final String BOOTSTRAP_PREFIX = "bootstrapProperties-";

    private NacosPropertySources() {
        // 工具类不允许实例化
    }

    /**
     * 判断一个属性源是不是来自 Nacos。
     *
     * @param sourceName 环境里的属性源名字
     * @return 命中 Nacos 属性源的命名规则时返回 true
     */
    public static boolean isNacosSource(String sourceName) {
        if (sourceName == null) {
            return false;
        }
        if (sourceName.startsWith(BOOTSTRAP_PREFIX)) {
            return true;
        }
        // 配置文件资源（application.yml / bootstrap.yml）也带 @ 字样，先排除
        if (sourceName.startsWith("Config resource")) {
            return false;
        }
        // config-import 形态：<group>@<dataId>
        return sourceName.indexOf('@') > 0;
    }

    /**
     * 从属性源名字里取出便于阅读的 dataId 标签。
     * <p>
     * bootstrap 形态去掉前缀后，剩下的 {@code <dataId>,<group>} 本身就能说明分组；
     * config-import 形态的 {@code <group>@<dataId>} 原样保留。
     *
     * @param sourceName 环境里的属性源名字
     * @return dataId 标签
     */
    public static String toDataIdLabel(String sourceName) {
        if (sourceName != null && sourceName.startsWith(BOOTSTRAP_PREFIX)) {
            return sourceName.substring(BOOTSTRAP_PREFIX.length());
        }
        return sourceName;
    }
}
