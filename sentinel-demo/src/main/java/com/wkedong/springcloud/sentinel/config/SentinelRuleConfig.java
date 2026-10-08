package com.wkedong.springcloud.sentinel.config;

import com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import com.alibaba.csp.sentinel.slots.system.SystemRule;
import com.alibaba.csp.sentinel.slots.system.SystemRuleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 用「编程式 API」在应用启动时加载四类规则（InitFunc / @PostConstruct 等价）。
 * <p>
 * 为什么用代码而不是控制台：控制台（Dashboard）推送的规则只存在内存里，重启即失效；
 * 教学 demo 先用代码把规则固定下来，保证「冷启动即可 curl 复现」，也正好展示了
 * {@code FlowRuleManager.loadRules(...)} 这类 API 的用法——控制台点「新增规则」，
 * 底层调用的就是同一批方法。
 * <p>
 * 四类规则与验证入口一一对应：
 * <ul>
 *   <li>流控 {@link FlowRuleManager} → {@code /demo/qps}、{@code /demo/concurrent}、{@code /demo/queue}</li>
 *   <li>熔断降级 {@link DegradeRuleManager} → {@code /degrade/slow}、{@code /degrade/exception-ratio}、{@code /degrade/exception-count}</li>
 *   <li>热点参数 {@link ParamFlowRuleManager} → {@code /hotspot?type=iphone}</li>
 *   <li>系统保护 {@link SystemRuleManager} → {@code /system/enable}（默认关闭，见 {@code sentinel.system-rule-enabled}）</li>
 * </ul>
 * 规则热更新/持久化的落地方式见文档「关键机制」一节（Nacos 数据源 + 控制台推送）。
 *
 * @author wkedong
 */
@Configuration
@EnableAspectJAutoProxy
public class SentinelRuleConfig {

    private static final Logger log = LoggerFactory.getLogger(SentinelRuleConfig.class);

    /** 热点参数限流的资源名（与 @SentinelResource 的 value 一致） */
    public static final String RES_HOTSPOT = "hotspot";

    /** 系统保护默认开关：默认关闭，避免系统规则「拦掉」其它演示资源的请求 */
    @Value("${sentinel.system-rule-enabled:false}")
    private boolean systemRuleEnabled;

    /**
     * 注册切面：{@code @SentinelResource} 依赖它才能生效。
     * <p>
     * Spring Cloud Alibaba 的 Sentinel starter 在多数场景已自动注册该 Bean，
     * 这里显式声明一次，保证「注解为什么没生效」这类问题不出现（同名 Bean 时后注册的不会覆盖）。
     */
    @Bean
    public SentinelResourceAspect sentinelResourceAspect() {
        return new SentinelResourceAspect();
    }

    /** 启动即加载规则；@PostConstruct 早于 Web 容器开始接收请求，避免「头几个请求没有规则」的窗口 */
    @PostConstruct
    public void initRules() {
        loadFlowRules();
        loadDegradeRules();
        loadParamFlowRules();
        loadSystemRules();
        log.info("=== Sentinel 编程式规则加载完成：flow={}, degrade={}, paramFlow={}, system={} ===",
                FlowRuleManager.getRules().size(),
                DegradeRuleManager.getRules().size(),
                ParamFlowRuleManager.getRules().size(),
                SystemRuleManager.getRules().size());
    }

    /** 流控规则：QPS 快速失败 / 并发线程数 / 排队等待 */
    private void loadFlowRules() {
        List<FlowRule> rules = new ArrayList<>();

        // 1) QPS 快速失败：每秒放行 2 个，超出立即抛 FlowException（blockHandler 接住）
        FlowRule qpsRule = new FlowRule();
        qpsRule.setResource("flow-qps");
        qpsRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        qpsRule.setCount(2);
        qpsRule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
        rules.add(qpsRule);

        // 2) 并发线程数模式：不看 QPS，只看「同一瞬间有多少线程在里面」
        //    适合保护「慢且吃资源」的下游——QPS 再低，只要每次都很慢也会把线程堆满
        FlowRule threadRule = new FlowRule();
        threadRule.setResource("flow-concurrent");
        threadRule.setGrade(RuleConstant.FLOW_GRADE_THREAD);
        threadRule.setCount(2);
        rules.add(threadRule);

        // 3) 排队等待：QPS 同样是 2，但多出来的请求不立刻拒绝，而是匀速排队
        //    maxQueueingTimeMs=2000 表示「我愿意最多等 2 秒」，等不到才抛 FlowException。
        //    效果：响应时间被拉长而不是被拒绝，适合削峰填谷（如异步任务提交）
        FlowRule queueRule = new FlowRule();
        queueRule.setResource("flow-queue");
        queueRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        queueRule.setCount(2);
        queueRule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_RATE_LIMITER);
        queueRule.setMaxQueueingTimeMs(2000);
        rules.add(queueRule);

        FlowRuleManager.loadRules(rules);
    }

    /** 熔断降级规则：慢调用比例 / 异常比例 / 异常数（三种 grade 各来一条，便于对照） */
    private void loadDegradeRules() {
        List<DegradeRule> rules = new ArrayList<>();

        // 1) 慢调用比例：统计窗口 1s 内至少 10 次请求，其中慢调用（>500ms）占比 ≥50% 就打开熔断，
        //    打开后 10s 内的请求直接快速失败（blockHandler 接住），10s 后进入半开试探。
        //
        //    为什么统计窗口用 1s 而不是生产上常见的 10s：
        //    熔断判定发生在「统计窗口结算」的时刻，而不是「第 N 个请求」——
        //    窗口取 10s 时，你会发现前 10 次慢调用全都正常通过，必须等窗口走完才看到熔断，
        //    很容易被误判成「规则没生效」。窗口缩到 1s，curl 循环一两秒内就能观察到状态切换，
        //    规则语义（最小请求数 + 比例阈值）完全一样，只是判定更频繁。
        //    生产上窗口取多少，取决于「你希望多快对下跌做出反应」与「样本量是否足够」的权衡。
        DegradeRule slowRtRule = new DegradeRule("degrade-slow")
                .setGrade(RuleConstant.DEGRADE_GRADE_RT)   // grade=0
                .setCount(500)                              // 慢调用判定阈值：500ms
                .setSlowRatioThreshold(0.5)                 // 慢调用比例阈值 50%
                .setMinRequestAmount(10)                    // 统计窗口内最小请求数
                .setStatIntervalMs(1000)                    // 统计窗口 1s（便于 curl 复现）
                .setTimeWindow(10);                         // 熔断时长 10s
        rules.add(slowRtRule);

        // 2) 异常比例：窗口内请求数 ≥5 且异常占比 ≥50% → 熔断 5s
        DegradeRule exceptionRatioRule = new DegradeRule("degrade-exception-ratio")
                .setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO)  // grade=1
                .setCount(0.5)
                .setMinRequestAmount(5)
                .setStatIntervalMs(10000)
                .setTimeWindow(5);
        rules.add(exceptionRatioRule);

        // 3) 异常数：窗口内异常「绝对条数」≥5 → 熔断 15s
        //    与异常比例的区别：低流量场景（每秒 1 个请求）比例很难达标，用绝对条数更灵敏
        DegradeRule exceptionCountRule = new DegradeRule("degrade-exception-count")
                .setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_COUNT)  // grade=2
                .setCount(5)
                .setMinRequestAmount(5)
                .setStatIntervalMs(10000)
                .setTimeWindow(15);
        rules.add(exceptionCountRule);

        DegradeRuleManager.loadRules(rules);
    }

    /**
     * 热点参数限流：只对「某个参数值」限流，而不是对整个资源限流。
     * <p>
     * 场景：商品详情页的爆款（id=iphone）把下游打满，但其它商品很闲——
     * 整资源限流会误伤长尾商品，热点参数限流只掐爆款。这里 2 QPS 只拦参数值 {@code iphone}，
     * 同样频次请求 {@code macbook} 完全不受影响（各自独立计数）。
     */
    private void loadParamFlowRules() {
        ParamFlowRule rule = new ParamFlowRule(RES_HOTSPOT)
                .setParamIdx(0)   // 第 0 个参数（即 type）作为热点参数
                .setCount(2)      // 同一参数值 2 QPS
                .setDurationInSec(1);
        // 例外项：热点参数规则支持给特定值单独配阈值，方便给 VIP 放行
        // ParamFlowItem item = new ParamFlowItem().setObject("vip").setClassType(String.class.getName()).setCount(10);
        // rule.setParamFlowItemList(Collections.singletonList(item));

        ParamFlowRuleManager.loadRules(Collections.singletonList(rule));
    }

    /**
     * 系统自适应保护（SystemRule）：站在「整台机器」视角的兜底，而不是单个接口。
     * <p>
     * 四个指标里，-1 表示「不启用该维度」（Sentinel 用 -1 而不是 0 表示关闭，
     * 因为 0 是合法阈值）：
     * <ul>
     *   <li>{@code qps} 入口总 QPS</li>
     *   <li>{@code avgRt} 所有入口的平均 RT（ms）</li>
     *   <li>{@code maxThread} 并发线程数</li>
     *   <li>{@code highestSystemLoad} / {@code highestCpuUsage} 系统负载与 CPU 使用率</li>
     * </ul>
     * <b>实测踩坑（很重要）</b>：{@code SystemRuleManager} 静态初始化时会预置一条
     * <b>默认系统规则 {@code avgRt=1000}（其余维度为 -1）</b>，而 {@code loadRules()} 是
     * 「按下标合并进容量为 4 的规则数组」，且<b>传入空集合会被当作无操作直接返回</b>。
     * 于是「清空系统规则」这件事做不到——不覆盖它，任何入口平均 RT 超过 1000ms 就会被拦。
     * <p>
     * 所以本方法<b>永远加载一条完整显式赋值的规则</b>（关闭时所有维度写 -1、数值为 0），
     * 用「全部显式赋值」把默认规则彻底顶替掉，避免一个隐藏的默认阈值偷偷生效。
     * <p>
     * 另外：系统规则会拦掉整个应用的入口流量，开着它就没法演示其它资源的转折点了，
     * 所以 demo 默认关闭，需要时用 {@code GET /system/enable?enabled=true} 打开。
     */
    private void loadSystemRules() {
        SystemRuleManager.loadRules(Collections.singletonList(buildSystemRule(systemRuleEnabled)));
        if (!systemRuleEnabled) {
            log.info("SystemRule 未启用（sentinel.system-rule-enabled=false），"
                    + "已用全 -1 规则覆盖默认的 avgRt=1000；可用 /system/enable?enabled=true 打开");
        }
    }

    /**
     * 构造一条「四个维度全部显式赋值」的系统规则。
     * <p>
     * enabled=true 时只放开入口 QPS=5，其余维度仍写 -1；
     * enabled=false 时全部写 -1（数值用 0 占位，Sentinel 看到 -1 就跳过该维度）。
     * 关键是<b>不留任何字段为「未设置」</b>，否则会继承默认规则里的 avgRt=1000。
     */
    public static SystemRule buildSystemRule(boolean enabled) {
        SystemRule rule = new SystemRule();
        // 不用三元表达式：写成显式分支，避免一行里塞两个语义
        if (enabled) {
            rule.setQps(5);
        } else {
            rule.setQps(-1);
        }
        rule.setAvgRt(-1);
        rule.setMaxThread(-1);
        rule.setHighestSystemLoad(-1);
        rule.setHighestCpuUsage(-1);
        return rule;
    }
}
