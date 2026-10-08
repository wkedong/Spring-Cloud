package com.wkedong.springcloud.nacos.web.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置预览：把「Nacos 里有什么」和「应用里读到了什么」并排放在一个响应里。
 * <p>
 * 分三块看：
 * <ol>
 *   <li>{@code nacosSources}：环境里真实存在的 Nacos 属性源（一个 dataId 一个），
 *       用来确认「哪几个 dataId 真的被拉下来了」；</li>
 *   <li>{@code probes}：四个探针 bean 的当前取值，用来看 {@code @RefreshScope} 的差别；</li>
 *   <li>{@code refreshEventCount}：收到过几次刷新事件，证明「值是刷出来的」而不是「重启出来的」。</li>
 * </ol>
 *
 * @author wkedong
 */
public class ConfigPreviewView {

    private String application;
    private List<String> activeProfiles = new ArrayList<String>();
    private List<NacosSourceView> nacosSources = new ArrayList<NacosSourceView>();
    /** key 为探针 bean 名，value 为该 bean 持有的配置快照 */
    private Map<String, Object> probes = new LinkedHashMap<String, Object>();
    /** 累计收到的 RefreshScopeRefreshedEvent 次数 */
    private int refreshEventCount;
    /** 进程已运行秒数：>0 且配置变了，才说明是「热更新」而不是重启 */
    private long uptimeSeconds;
    /** 本实例真实监听端口（多实例演示时确认自己在跟谁说话） */
    private int localPort;

    /**
     * 单个 Nacos 属性源的摘要。
     */
    public static class NacosSourceView {

        /** 环境里的原始属性源名字（两种加载方式的命名不同，保留原样便于对照） */
        private String propertySourceName;
        /** 从名字里解析出来的 dataId 标签 */
        private String dataId;
        private String sourceClass;
        private int keyCount;
        private Map<String, Object> keys = new LinkedHashMap<String, Object>();

        public String getPropertySourceName() {
            return propertySourceName;
        }

        public void setPropertySourceName(String propertySourceName) {
            this.propertySourceName = propertySourceName;
        }

        public String getDataId() {
            return dataId;
        }

        public void setDataId(String dataId) {
            this.dataId = dataId;
        }

        public String getSourceClass() {
            return sourceClass;
        }

        public void setSourceClass(String sourceClass) {
            this.sourceClass = sourceClass;
        }

        public int getKeyCount() {
            return keyCount;
        }

        public void setKeyCount(int keyCount) {
            this.keyCount = keyCount;
        }

        public Map<String, Object> getKeys() {
            return keys;
        }

        public void setKeys(Map<String, Object> keys) {
            this.keys = keys;
        }
    }

    public String getApplication() {
        return application;
    }

    public void setApplication(String application) {
        this.application = application;
    }

    public List<String> getActiveProfiles() {
        return activeProfiles;
    }

    public void setActiveProfiles(List<String> activeProfiles) {
        this.activeProfiles = activeProfiles;
    }

    public List<NacosSourceView> getNacosSources() {
        return nacosSources;
    }

    public void setNacosSources(List<NacosSourceView> nacosSources) {
        this.nacosSources = nacosSources;
    }

    public Map<String, Object> getProbes() {
        return probes;
    }

    public void setProbes(Map<String, Object> probes) {
        this.probes = probes;
    }

    public int getRefreshEventCount() {
        return refreshEventCount;
    }

    public void setRefreshEventCount(int refreshEventCount) {
        this.refreshEventCount = refreshEventCount;
    }

    public long getUptimeSeconds() {
        return uptimeSeconds;
    }

    public void setUptimeSeconds(long uptimeSeconds) {
        this.uptimeSeconds = uptimeSeconds;
    }

    public int getLocalPort() {
        return localPort;
    }

    public void setLocalPort(int localPort) {
        this.localPort = localPort;
    }
}
