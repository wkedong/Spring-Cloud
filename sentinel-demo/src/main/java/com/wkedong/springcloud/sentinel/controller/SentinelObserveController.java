package com.wkedong.springcloud.sentinel.controller;

import com.alibaba.csp.sentinel.node.ClusterNode;
import com.alibaba.csp.sentinel.node.Node;
import com.alibaba.csp.sentinel.slotchain.ResourceWrapper;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import com.alibaba.csp.sentinel.slots.clusterbuilder.ClusterBuilderSlot;
import com.alibaba.csp.sentinel.slots.system.SystemRule;
import com.alibaba.csp.sentinel.slots.system.SystemRuleManager;
import com.wkedong.springcloud.sentinel.service.DegradeDemoService;
import com.wkedong.springcloud.sentinel.service.FlowDemoService;
import com.wkedong.springcloud.sentinel.service.HotspotService;
import com.wkedong.springcloud.sentinel.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 可观测端点：把「内存里到底生效了哪些规则」「各资源此刻的实时统计」直接暴露成 JSON。
 * <p>
 * 为什么需要它：Sentinel Dashboard 是最顺手的观测方式，但它是一个独立的 Java 服务
 * （本机不一定常驻），而 {@code curl} 永远可用。这两个端点让「规则是否加载成功」
 * 「熔断到底有没有打开」变成可断言的事实，而不是「看控制台猜」——
 * 教学场景下这一点比控制台更重要，因为学生可以自己复现。
 * <p>
 * 统计来源是 Sentinel 自己的 {@link ClusterBuilderSlot}（贯穿整条 Slot 链的统计节点）：
 * passQps/blockQps/successQps/exceptionQps/rt 全是引擎真实值，
 * 不是本模块额外加的计数器——这样做的好处是「看到的数字就是规则判定时用的数字」。
 *
 * @author wkedong
 */
@RestController
@RequestMapping("/sentinel")
public class SentinelObserveController {

    /**
     * 观察统计的资源清单：只列「注解资源」，URL 资源由下面的循环动态补上。
     * <p>
     * 为什么不在这里硬编码 URL 资源名：Spring Cloud Alibaba 2021.x 注册的是
     * {@code SentinelWebInterceptor}（urlPatterns = {@code /**}），它建的 URL 资源名是
     * <b>纯路径</b>（{@code /demo/qps}），<b>不带</b> {@code GET:} 前缀——
     * 而 {@code GET:/path} 那种命名来自老版本的 {@code CommonFilter}。
     * 写错了规则会「加载成功但永不命中」，所以这里改成从
     * {@link ClusterBuilderSlot#getClusterNodeMap()} 动态枚举，看到的就是真实资源名。
     */
    private static final List<String> OBSERVED_RESOURCES = Arrays.asList(
            "flow-qps", "flow-concurrent", "flow-queue", "hotspot",
            "degrade-slow", "degrade-exception-ratio", "degrade-exception-count");

    private final FlowDemoService flowDemoService;
    private final DegradeDemoService degradeDemoService;
    private final HotspotService hotspotService;

    public SentinelObserveController(FlowDemoService flowDemoService,
                                     DegradeDemoService degradeDemoService,
                                     HotspotService hotspotService) {
        this.flowDemoService = flowDemoService;
        this.degradeDemoService = degradeDemoService;
        this.hotspotService = hotspotService;
    }

    /** 当前内存里生效的全部规则（四类），字段名与 Dashboard 上的列一一对应 */
    @GetMapping("/rules")
    public ApiResponse<Map<String, Object>> rules() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("flow(流控)", flowRules());
        data.put("degrade(熔断降级)", degradeRules());
        data.put("paramFlow(热点参数)", paramFlowRules());
        data.put("system(系统保护)", systemRules());
        data.put("counts", counts());
        return ApiResponse.ok(data);
    }

    /** 各资源实时统计 + blockHandler/fallback 触发次数（把「谁被谁拦了」一次看全） */
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("resources", resourceStats());
        data.put("counters", counters());
        data.put("explain", "pass/block/success/exception 取自 Sentinel ClusterNode；"
                + "blockQps>0 表示该资源此刻正被规则拦截。"
                + "注意：熔断是否打开，看 counters 里的「××× 快速失败次数」——"
                + "它涨了就说明熔断已打开");
        return ApiResponse.ok(data);
    }

    /**
     * 读每个资源的实时统计。
     * <p>
     * 资源清单不写死，而是直接枚举 {@link ClusterBuilderSlot#getClusterNodeMap()}——
     * 这样连「Web 拦截器自动建的 URL 资源」也能被看到（Spring Cloud Alibaba 2021.x
     * 注册的是 {@code SentinelWebInterceptor}，它按 URL 建资源；
     * 资源名是<b>纯路径</b>，如 {@code /demo/qps}；老教程里常见的 {@code GET:/path}
     * 来自 {@code CommonFilter}，两者不是一回事）。想知道 URL 资源到底叫什么，看这里最准。
     */
    private List<Map<String, Object>> resourceStats() {
        List<Map<String, Object>> list = new ArrayList<>();
        // 先列注解资源（保证顺序稳定），再补上自动创建的 URL 资源
        List<String> names = new ArrayList<>(OBSERVED_RESOURCES);
        for (Map.Entry<ResourceWrapper, ClusterNode> entry : ClusterBuilderSlot.getClusterNodeMap().entrySet()) {
            String name = entry.getKey().getName();
            if (!names.contains(name)) {
                names.add(name);
            }
        }
        for (String resource : names) {
            ClusterNode node = ClusterBuilderSlot.getClusterNode(resource);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("resource", resource);
            if (node == null) {
                item.put("state", "尚未被访问（ClusterNode 为空）");
                list.add(item);
                continue;
            }
            item.put("passQps", round(node.passQps()));
            item.put("blockQps", round(node.blockQps()));
            item.put("successQps", round(node.successQps()));
            item.put("exceptionQps", round(node.exceptionQps()));
            item.put("avgRt(ms)", round(node.avgRt()));
            item.put("totalPass", node.totalPass());
            item.put("totalBlock", node.blockRequest());
            item.put("totalSuccess", node.totalSuccess());
            item.put("totalException", node.totalException());
            item.put("curThreadNum", node.curThreadNum());
            list.add(item);
        }
        return list;
    }

    private Map<String, Object> counts() {
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("flow", FlowRuleManager.getRules().size());
        counts.put("degrade", DegradeRuleManager.getRules().size());
        counts.put("paramFlow", ParamFlowRuleManager.getRules().size());
        counts.put("system", SystemRuleManager.getRules().size());
        return counts;
    }

    private List<Map<String, Object>> flowRules() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (FlowRule rule : FlowRuleManager.getRules()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("resource", rule.getResource());
            item.put("grade", rule.getGrade() == RuleConstant.FLOW_GRADE_QPS ? "QPS" : "并发线程数");
            item.put("count", rule.getCount());
            item.put("controlBehavior", behaviorName(rule.getControlBehavior()));
            // maxQueueingTimeMs 只对「排队等待」有意义；其它行为下这个字段是默认值，打印出来反而误导
            if (rule.getControlBehavior() == RuleConstant.CONTROL_BEHAVIOR_RATE_LIMITER) {
                item.put("maxQueueingTimeMs", rule.getMaxQueueingTimeMs());
            }
            item.put("strategy", rule.getStrategy());
            list.add(item);
        }
        return list;
    }

    private List<Map<String, Object>> degradeRules() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (DegradeRule rule : DegradeRuleManager.getRules()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("resource", rule.getResource());
            item.put("grade", gradeName(rule.getGrade()));
            item.put("count", rule.getCount());
            // slowRatioThreshold 只在「慢调用比例」grade 下有效，其它 grade 打出来是默认值 1.0
            if (rule.getGrade() == RuleConstant.DEGRADE_GRADE_RT) {
                item.put("slowRatioThreshold", rule.getSlowRatioThreshold());
            }
            item.put("minRequestAmount", rule.getMinRequestAmount());
            item.put("statIntervalMs", rule.getStatIntervalMs());
            item.put("timeWindow(s)", rule.getTimeWindow());
            list.add(item);
        }
        return list;
    }

    private List<Map<String, Object>> paramFlowRules() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ParamFlowRule rule : ParamFlowRuleManager.getRules()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("resource", rule.getResource());
            item.put("paramIdx", rule.getParamIdx());
            item.put("count(单值阈值)", rule.getCount());
            item.put("durationInSec", rule.getDurationInSec());
            item.put("controlBehavior", behaviorName(rule.getControlBehavior()));
            list.add(item);
        }
        return list;
    }

    private List<Map<String, Object>> systemRules() {
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
        return list;
    }

    private Map<String, Object> counters() {
        Map<String, Object> counters = new LinkedHashMap<>();
        counters.putAll(flowDemoService.counters());
        counters.putAll(degradeDemoService.counters());
        counters.putAll(hotspotService.counters());
        return counters;
    }

    private String behaviorName(int behavior) {
        switch (behavior) {
            case 0:
                return "快速失败(0)";
            case 1:
                return "WarmUp(1)";
            case 2:
                return "排队等待(2)";
            case 3:
                return "WarmUp+排队(3)";
            default:
                return "未知(" + behavior + ")";
        }
    }

    private String gradeName(int grade) {
        switch (grade) {
            case 0:
                return "慢调用比例(0)";
            case 1:
                return "异常比例(1)";
            case 2:
                return "异常数(2)";
            default:
                return "未知(" + grade + ")";
        }
    }

    private double round(double value) {
        return Math.round(value * 100) / 100.0D;
    }

    /** 保留给「想看原始 Node 对象」的场景（Node 接口是只读的，误用风险很低） */
    protected Node node(String resource) {
        return ClusterBuilderSlot.getClusterNode(resource);
    }
}
