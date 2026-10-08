package com.wkedong.springcloud.sentinel.controller;

import com.alibaba.csp.sentinel.Constants;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.system.SystemRule;
import com.alibaba.csp.sentinel.slots.system.SystemRuleManager;
import com.wkedong.springcloud.sentinel.config.SentinelRuleConfig;
import com.wkedong.springcloud.sentinel.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统自适应保护（{@code SystemRule}）的开关与探测入口。
 * <p>
 * 为什么做成「可开关」而不是启动就生效：系统规则的判定对象是**整个应用的入口流量**
 * （所有 URL 资源共享一个 context），开着它会把其它演示资源的请求一起拦掉，
 * 那样就看不到 QPS 流控、熔断这些规则的转折点了。教学 demo 里默认关闭，
 * 需要观察时用 {@code /system/enable?enabled=true} 临时打开。
 * <p>
 * 与单资源规则最本质的区别：单资源规则回答「这个接口该不该限」，
 * 系统规则回答「这台机器现在还能不能接活」——它不关心是哪个接口，
 * 只看入口 QPS / 平均 RT / 并发线程数 / 系统 Load 这些**整机指标**，
 * 因此它天然是「最后的兜底防线」，通常阈值设得比业务规则宽松得多。
 *
 * @author wkedong
 */
@RestController
@RequestMapping("/system")
public class SystemController {

    /** 系统规则的入口 QPS 阈值（教学值，故意设得很小以便 curl 复现） */
    public static final double SYSTEM_QPS_LIMIT = 5.0D;

    /** 简单探测接口：用它观察系统规则打开后「入口 QPS 超 5」的拦截效果 */
    @GetMapping("/probe")
    public ApiResponse<Map<String, Object>> probe() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("resource", "系统规则：探测接口");
        data.put("explain", "系统规则不针对具体接口，而是看整个应用的入口 QPS/RT/线程数");
        return ApiResponse.ok(data);
    }

    /**
     * 打开/关闭系统规则。
     * <pre>curl "http://127.0.0.1:8220/system/enable?enabled=true"</pre>
     * 打开后立刻连打 10 次 probe：入口 QPS 超过 5，第 6 个起会被拦下
     * （Web 拦截器在进入 Controller 之前判定，返回 HTTP 429）。
     * <p>
     * 注意这里复用 {@code SentinelRuleConfig.buildSystemRule}：必须「四个维度全部显式赋值」，
     * 否则 {@code SystemRuleManager} 预置的默认规则（avgRt=1000）会残留下来继续拦请求——
     * 详见该方法的注释。
     */
    @GetMapping("/enable")
    public ApiResponse<Map<String, Object>> enable(@RequestParam("enabled") boolean enabled) {
        SystemRuleManager.loadRules(Collections.singletonList(SentinelRuleConfig.buildSystemRule(enabled)));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("systemRuleEnabled", enabled);
        data.put("loadedRules", SystemRuleManager.getRules().size());
        // 不用三元表达式：显式分支
        if (enabled) {
            data.put("qpsLimit", SystemController.SYSTEM_QPS_LIMIT);
        } else {
            data.put("qpsLimit", null);
        }
        data.put("remark", "四个维度全部显式赋值（-1 = 不启用该维度），避免默认规则 avgRt=1000 残留");
        return ApiResponse.ok(data);
    }

    /**
     * 给 <b>URL 资源</b> 加/删一条流控规则，用来验证「Web 拦截器那一层」的拦截效果。
     * <pre>
     * curl "http://127.0.0.1:8220/system/url-rule?enabled=true"
     * for i in $(seq 1 5); do curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:8220/system/probe; done
     * # 前 2 个 200，第 3 个起 429
     * curl "http://127.0.0.1:8220/system/url-rule?enabled=false"
     * </pre>
     * <b>资源名踩坑记录</b>：Spring Cloud Alibaba 2021.x 注册的是
     * {@code SentinelWebInterceptor}（urlPatterns = {@code /**}），它建的 URL 资源名是
     * <b>纯路径</b>（如 {@code /system/probe}），<b>不带</b> {@code GET:} 前缀——
     * {@code GET:/path} 那种命名来自老版本 {@code CommonFilter}，两者不是一回事。
     * 资源名写错，规则会加载成功但永远不命中（正是「规则明明加了却没生效」的经典原因）。
     * 不知道真实资源名时，看 {@code /sentinel/status} 打出来的资源清单最准。
     * 为什么值得单独演示：{@code @SentinelResource} 保护的是「方法」，而 URL 资源保护的是「路径」，
     * 两者是**两条独立的防线**，谁先被拦取决于谁先进入 Slot 链——Web 拦截器在 Controller 之前，
     * 所以 URL 规则先命中；此时异常由 {@code BlockExceptionHandler} 处理（HTTP 429），
     * 而不会走到方法上的 {@code blockHandler}。生产上这正是「网关/入口限流」与「业务方法保护」的分工。
     */
    @GetMapping("/url-rule")
    public ApiResponse<Map<String, Object>> urlRule(
            @RequestParam("enabled") boolean enabled,
            @RequestParam(value = "path", defaultValue = "/system/probe") String path) {
        String resource = path;
        if (enabled) {
            FlowRule rule = new FlowRule(resource);
            rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
            rule.setCount(2);
            rule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
            FlowRuleManager.loadRules(buildUrlLayerRules(rule));
        } else {
            FlowRuleManager.loadRules(buildUrlLayerRules());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("urlResource", resource);
        data.put("enabled", enabled);
        // 不用三元表达式：显式分支
        if (enabled) {
            data.put("qpsLimit", 2);
        } else {
            data.put("qpsLimit", null);
        }
        data.put("explain", "URL 资源规则由 Web 拦截器在进入 Controller 之前判定，被拦时返回 HTTP 429，"
                + "不会走到 @SentinelResource 的 blockHandler");
        data.put("note", "本接口只维护 URL 层规则，注解资源规则（flow-qps 等）始终保持不变");
        return ApiResponse.ok(data);
    }

    /**
     * 把「URL 层规则」与「注解资源规则」分开维护。
     * <p>
     * {@code FlowRuleManager.loadRules()} 是<b>整体替换</b>而不是追加：如果直接把 URL 规则丢进去，
     * 启动时加载的 flow-qps / flow-concurrent / flow-queue 三条规则会被一并冲掉。
     * 这是很实用的一个坑——「加了一条规则，别的规则全没了」几乎都是这个原因。
     */
    private List<FlowRule> buildUrlLayerRules(FlowRule... urlRules) {
        List<FlowRule> merged = new ArrayList<>();
        for (FlowRule existing : FlowRuleManager.getRules()) {
            if (!isUrlResource(existing.getResource())) {
                merged.add(existing);
            }
        }
        merged.addAll(Arrays.asList(urlRules));
        return merged;
    }

    /**
     * 判断一条规则是不是「URL 层」的。
     * <p>
     * 本模块的注解资源名都是 {@code flow-*} / {@code degrade-*} / {@code hotspot} 这类短横线风格，
     * 而 URL 资源名以 {@code /} 开头，据此区分即可（比硬编码资源名清单更耐用）。
     */
    private boolean isUrlResource(String resource) {
        return resource != null && resource.startsWith("/");
    }

    /** 看当前生效的系统规则（-1 的字段即未启用） */    @GetMapping("/rules")
    public ApiResponse<List<Map<String, Object>>> rules() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (SystemRule rule : SystemRuleManager.getRules()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("highestSystemLoad", rule.getHighestSystemLoad());
            item.put("highestCpuUsage", rule.getHighestCpuUsage());
            item.put("avgRt(ms)", rule.getAvgRt());
            item.put("maxThread", rule.getMaxThread());
            item.put("qps", rule.getQps());
            list.add(item);
        }
        return ApiResponse.ok(list);
    }

    /**
     * 把 SystemRuleManager 内部「真正在用的阈值」打出来。
     * <p>
     * 为什么需要这个端点：{@code getRules()} 返回的是规则对象，而实际判定用的是
     * {@code SystemRuleManager} 里几个静态字段（{@code getInboundQpsThreshold()}、
     * {@code getRtThreshold()} …）。排查「规则加载了但没生效」时，看这些字段比看规则对象准得多。
     */
    @GetMapping("/thresholds")
    public ApiResponse<Map<String, Object>> thresholds() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("checkSystemStatus(是否启用系统检查)", SystemRuleManager.getCheckSystemStatus());
        data.put("inboundQpsThreshold", SystemRuleManager.getInboundQpsThreshold());
        data.put("rtThreshold", SystemRuleManager.getRtThreshold());
        data.put("maxThreadThreshold", SystemRuleManager.getMaxThreadThreshold());
        data.put("systemLoadThreshold", SystemRuleManager.getSystemLoadThreshold());
        data.put("cpuUsageThreshold", SystemRuleManager.getCpuUsageThreshold());
        data.put("currentSystemAvgLoad", SystemRuleManager.getCurrentSystemAvgLoad());
        data.put("currentCpuUsage", SystemRuleManager.getCurrentCpuUsage());
        data.put("rulesCount", SystemRuleManager.getRules().size());
        return ApiResponse.ok(data);
    }

    /**
     * 读「全局入口节点」({@code Constants.ENTRY_NODE}) 的实时值。
     * <p>
     * 这是排查系统规则「配了却不生效」的关键：系统规则的入口 QPS 判定读的是
     * {@code Constants.ENTRY_NODE.passQps()}，而这个节点<b>只统计 EntryType.IN 的资源</b>
     * （见 {@code StatisticSlot}：{@code if (resourceWrapper.getEntryType() == EntryType.IN)}）。
     * 如果这里的 passQps 一直是 0，说明流量压根没以 IN 类型进入 Sentinel，
     * 那么无论 qps 阈值配成多少都不可能触发——这比对着规则对象猜有效得多。
     */
    @GetMapping("/entry-node")
    public ApiResponse<Map<String, Object>> entryNode() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", Constants.ENTRY_NODE.getName());
        data.put("passQps(系统规则判定用的入口 QPS)", Constants.ENTRY_NODE.passQps());
        data.put("blockQps", Constants.ENTRY_NODE.blockQps());
        data.put("successQps", Constants.ENTRY_NODE.successQps());
        data.put("exceptionQps", Constants.ENTRY_NODE.exceptionQps());
        data.put("avgRt(ms)", Constants.ENTRY_NODE.avgRt());
        data.put("curThreadNum", Constants.ENTRY_NODE.curThreadNum());
        data.put("totalPass(累计)", Constants.ENTRY_NODE.totalPass());
        return ApiResponse.ok(data);
    }

    /** 规则常量说明用：把本模块用到的 grade/behavior 数值暴露出来，方便对照控制台上的下拉选项 */
    @GetMapping("/constants")
    public ApiResponse<Map<String, Object>> constants() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("FLOW_GRADE_QPS", RuleConstant.FLOW_GRADE_QPS);
        data.put("FLOW_GRADE_THREAD", RuleConstant.FLOW_GRADE_THREAD);
        data.put("CONTROL_BEHAVIOR_DEFAULT(快速失败)", RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
        data.put("CONTROL_BEHAVIOR_RATE_LIMITER(排队等待)", RuleConstant.CONTROL_BEHAVIOR_RATE_LIMITER);
        data.put("DEGRADE_GRADE_RT(慢调用比例)", RuleConstant.DEGRADE_GRADE_RT);
        data.put("DEGRADE_GRADE_EXCEPTION_RATIO(异常比例)", RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO);
        data.put("DEGRADE_GRADE_EXCEPTION_COUNT(异常数)", RuleConstant.DEGRADE_GRADE_EXCEPTION_COUNT);
        return ApiResponse.ok(data);
    }
}
