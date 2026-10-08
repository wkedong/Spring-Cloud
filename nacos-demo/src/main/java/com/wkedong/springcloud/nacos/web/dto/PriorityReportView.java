package com.wkedong.springcloud.nacos.web.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置优先级报告：同一个 key 在环境里到底被谁决定了。
 * <p>
 * 做法不是「背结论」，而是现场算：
 * 遍历 {@code ConfigurableEnvironment.getPropertySources()}，属性源是从高到低排的，
 * 第一个包含该 key 的属性源就是生效来源（{@code candidates} 里同时列出其余候选值，
 * 于是「Nacos 远端写了什么、本地 yml 写了什么、命令行传了什么」一目了然）。
 *
 * @author wkedong
 */
public class PriorityReportView {

    /** 属性源顺序（越靠前优先级越高），名字即来源类型 */
    private List<String> propertySourceOrder = new ArrayList<String>();
    private List<PriorityEntry> entries = new ArrayList<PriorityEntry>();
    /** 本次启动的命令行参数（-jar 后面跟的参数） */
    private Map<String, String> commandLineArgs = new LinkedHashMap<String, String>();

    /**
     * 单个 key 的优先级明细。
     */
    public static class PriorityEntry {

        private String key;
        /** 实际生效值 = environment.getProperty(key) */
        private String effectiveValue;
        /** 生效的属性源名（第一个包含该 key 的属性源） */
        private String winningSource;
        /** 所有「包含该 key」的属性源名 → 值，按优先级从高到低 */
        private Map<String, String> candidates = new LinkedHashMap<String, String>();

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getEffectiveValue() {
            return effectiveValue;
        }

        public void setEffectiveValue(String effectiveValue) {
            this.effectiveValue = effectiveValue;
        }

        public String getWinningSource() {
            return winningSource;
        }

        public void setWinningSource(String winningSource) {
            this.winningSource = winningSource;
        }

        public Map<String, String> getCandidates() {
            return candidates;
        }

        public void setCandidates(Map<String, String> candidates) {
            this.candidates = candidates;
        }
    }

    public List<String> getPropertySourceOrder() {
        return propertySourceOrder;
    }

    public void setPropertySourceOrder(List<String> propertySourceOrder) {
        this.propertySourceOrder = propertySourceOrder;
    }

    public List<PriorityEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<PriorityEntry> entries) {
        this.entries = entries;
    }

    public Map<String, String> getCommandLineArgs() {
        return commandLineArgs;
    }

    public void setCommandLineArgs(Map<String, String> commandLineArgs) {
        this.commandLineArgs = commandLineArgs;
    }
}
