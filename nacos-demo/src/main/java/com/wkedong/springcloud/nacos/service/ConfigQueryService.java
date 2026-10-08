package com.wkedong.springcloud.nacos.service;

import com.wkedong.springcloud.nacos.config.DemoProperties;
import com.wkedong.springcloud.nacos.config.FrozenValueProbe;
import com.wkedong.springcloud.nacos.config.PlainProperties;
import com.wkedong.springcloud.nacos.config.RefreshValueProbe;
import com.wkedong.springcloud.nacos.web.dto.ConfigPreviewView;
import com.wkedong.springcloud.nacos.web.dto.PriorityReportView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 配置中心的「读侧」封装：把环境里的配置来源、探针取值、优先级关系整理成可 curl 的视图。
 * <p>
 * 为什么不直接在 Controller 里读 {@code environment}：这里做的是「解释配置从哪来」，
 * 属于业务语义（教学语义），控制器只该关心 URL 与响应包装。
 * <p>
 * 刷新计数说明：Nacos 长轮询发现配置变更后会发布 {@code RefreshEvent}，
 * Spring Cloud 消费它并发布 {@link RefreshScopeRefreshedEvent}。
 * 因此这个计数 > 0 且进程 uptime 持续增长时，才能断定「值是新拉的，不是重启出来的」。
 *
 * @author wkedong
 */
@Service
public class ConfigQueryService {

    private static final Logger log = LoggerFactory.getLogger(ConfigQueryService.class);

    /** Nacos 组合属性源在外层环境里的名字（部分加载方式下的形态） */
    private static final String NACOS_COMPOSITE_SOURCE_NAME = "NACOS";

    /** 优先级实验观察的样本 key，覆盖「远端独有 / 本地独有 / 三方同名」三类 */
    private static final List<String> PRIORITY_KEYS = Arrays.asList(
            "nacos.demo.title",
            "nacos.demo.priority",
            "nacos.demo.order-probe",
            "nacos.demo.profile-value",
            "nacos.demo.profileValue",
            "nacos.demo.shared-value",
            "nacos.demo.remote-only",
            "nacos.demo.local-only",
            "nacos.demo.feature-flag",
            "nacos.demo.max-batch-size");

    private final ConfigurableEnvironment environment;
    private final ApplicationArguments applicationArguments;
    private final DiscoveryService discoveryService;

    /** 四个探针 bean：两两对照 @RefreshScope 与绑定方式 */
    private final DemoProperties demoProperties;
    private final PlainProperties plainProperties;
    private final RefreshValueProbe refreshValueProbe;
    private final FrozenValueProbe frozenValueProbe;

    private final AtomicInteger refreshEventCount = new AtomicInteger();
    private final long startMillis = System.currentTimeMillis();

    public ConfigQueryService(ConfigurableEnvironment environment,
                              ApplicationArguments applicationArguments,
                              DiscoveryService discoveryService,
                              DemoProperties demoProperties,
                              PlainProperties plainProperties,
                              RefreshValueProbe refreshValueProbe,
                              FrozenValueProbe frozenValueProbe) {
        this.environment = environment;
        this.applicationArguments = applicationArguments;
        this.discoveryService = discoveryService;
        this.demoProperties = demoProperties;
        this.plainProperties = plainProperties;
        this.refreshValueProbe = refreshValueProbe;
        this.frozenValueProbe = frozenValueProbe;
    }

    /**
     * 收到一次作用域刷新事件就记一笔，用 {@code refreshEventCount} 暴露出去。
     */
    @EventListener
    public void onRefreshScopeRefreshed(RefreshScopeRefreshedEvent event) {
        int count = refreshEventCount.incrementAndGet();
        log.info("收到配置刷新事件（第 {} 次），最新 title = {}", count, environment.getProperty("nacos.demo.title"));
    }

    /**
     * 配置预览：Nacos 里有什么、应用读到了什么、刷过几次。
     */
    public ConfigPreviewView preview() {
        ConfigPreviewView view = new ConfigPreviewView();
        view.setApplication(environment.getProperty("spring.application.name"));
        view.setActiveProfiles(Arrays.asList(environment.getActiveProfiles()));
        view.setRefreshEventCount(refreshEventCount.get());
        view.setUptimeSeconds((System.currentTimeMillis() - startMillis) / 1000L);
        view.setLocalPort(discoveryService.localPort());
        collectNacosSources(view);
        view.setProbes(probeValues());
        return view;
    }

    /**
     * 四个探针 bean 的当前取值。
     * <p>
     * 这里用 Map 而不是为每组取值建 DTO：探针是「对照实验的观测点」，
     * 字段会随教学需要增删，硬造四个结构相同的 DTO 只会增加维护面。
     */
    private Map<String, Object> probeValues() {
        Map<String, Object> probes = new LinkedHashMap<String, Object>();
        probes.put("refreshValueProbe（@Value + @RefreshScope）", refreshProbe());
        probes.put("frozenValueProbe（@Value 无 @RefreshScope）", frozenProbe());
        probes.put("demoProperties（@ConfigurationProperties + @RefreshScope）", propertiesProbe(
                demoProperties.getTitle(), demoProperties.getPriority(), demoProperties.getProfileValue(),
                demoProperties.getSharedValue(), demoProperties.getRemoteOnly(),
                demoProperties.isFeatureFlag(), demoProperties.getMaxBatchSize()));
        probes.put("plainProperties（@ConfigurationProperties 无 @RefreshScope）", propertiesProbe(
                plainProperties.getTitle(), plainProperties.getPriority(), plainProperties.getProfileValue(),
                plainProperties.getSharedValue(), plainProperties.getRemoteOnly(),
                plainProperties.isFeatureFlag(), plainProperties.getMaxBatchSize()));
        return probes;
    }

    private Map<String, Object> refreshProbe() {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("title", refreshValueProbe.getTitle());
        values.put("profileValue", refreshValueProbe.getProfileValue());
        values.put("sharedValue", refreshValueProbe.getSharedValue());
        values.put("maxBatchSize", refreshValueProbe.getMaxBatchSize());
        return values;
    }

    private Map<String, Object> frozenProbe() {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("title", frozenValueProbe.getTitle());
        values.put("profileValue", frozenValueProbe.getProfileValue());
        values.put("sharedValue", frozenValueProbe.getSharedValue());
        values.put("maxBatchSize", frozenValueProbe.getMaxBatchSize());
        return values;
    }

    private Map<String, Object> propertiesProbe(String title, String priority, String profileValue,
                                                String sharedValue, String remoteOnly,
                                                boolean featureFlag, int maxBatchSize) {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("title", title);
        values.put("priority", priority);
        values.put("profileValue", profileValue);
        values.put("sharedValue", sharedValue);
        values.put("remoteOnly", remoteOnly);
        values.put("featureFlag", featureFlag);
        values.put("maxBatchSize", maxBatchSize);
        return values;
    }

    /**
     * 找出环境里所有来自 Nacos 的属性源，逐个列出它对应的 dataId 与键值。
     * <p>
     * dataId 的呈现形式就是属性源名字，所以「哪几个 dataId 被拉下来了」无需猜：
     * 数一数这里有几个条目即可。命名规则由 {@link NacosPropertySources} 统一判断：
     * bootstrap 模式是 {@code bootstrapProperties-<dataId>,<group>}，
     * config-import 模式是 {@code <group>@<dataId>}，外加一种组合源名为 NACOS 的形态。
     */
    private void collectNacosSources(ConfigPreviewView view) {
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (source instanceof CompositePropertySource
                    && NACOS_COMPOSITE_SOURCE_NAME.equals(source.getName())) {
                for (PropertySource<?> child : ((CompositePropertySource) source).getPropertySources()) {
                    view.getNacosSources().add(toNacosSourceView(child.getName(), child));
                }
                continue;
            }
            if (NacosPropertySources.isNacosSource(source.getName())) {
                view.getNacosSources().add(toNacosSourceView(source.getName(), source));
            }
        }
    }

    private ConfigPreviewView.NacosSourceView toNacosSourceView(String propertySourceName, PropertySource<?> source) {
        ConfigPreviewView.NacosSourceView childView = new ConfigPreviewView.NacosSourceView();
        childView.setPropertySourceName(propertySourceName);
        childView.setDataId(NacosPropertySources.toDataIdLabel(propertySourceName));
        childView.setSourceClass(source.getClass().getSimpleName());
        Map<String, Object> keys = readKeys(source);
        childView.getKeys().putAll(keys);
        childView.setKeyCount(keys.size());
        return childView;
    }

    /**
     * 尽最大努力读出属性源里的键值。
     * <p>
     * bootstrap 模式下的 Nacos 属性源外面还套了一层包装（{@code getSource()} 返回的是
     * 真正的属性源而不是 Map），所以要往里剥；剥不动就退化为按名字枚举。
     */
    private Map<String, Object> readKeys(PropertySource<?> source) {
        Map<String, Object> keys = new LinkedHashMap<String, Object>();
        Object target = source;
        for (int depth = 0; depth < 5 && target instanceof PropertySource; depth++) {
            target = ((PropertySource<?>) target).getSource();
        }
        if (target instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) target).entrySet()) {
                keys.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return keys;
        }
        if (source instanceof EnumerablePropertySource) {
            for (String name : ((EnumerablePropertySource<?>) source).getPropertyNames()) {
                keys.put(name, source.getProperty(name));
            }
        }
        return keys;
    }

    /**
     * 优先级报告：属性源顺序 + 每个样本 key 的生效来源与全部候选值。
     * <p>
     * 「生效来源」的判定用的是 Spring 自己的规则：{@code MutablePropertySources}
     * 按优先级从高到低排列，第一个 {@code containsProperty} 命中的就是赢家。
     * 所以这个端点的输出本身就是规则，不需要背口诀。
     */
    public PriorityReportView priority() {
        PriorityReportView report = new PriorityReportView();

        for (PropertySource<?> source : environment.getPropertySources()) {
            report.getPropertySourceOrder().add(source.getName());
        }

        for (String key : PRIORITY_KEYS) {
            PriorityReportView.PriorityEntry entry = new PriorityReportView.PriorityEntry();
            entry.setKey(key);
            entry.setEffectiveValue(environment.getProperty(key));
            for (PropertySource<?> source : environment.getPropertySources()) {
                Object value = readQuietly(source, key);
                if (value == NOT_FOUND) {
                    continue;
                }
                entry.getCandidates().put(source.getName(), value == null ? "<null>" : String.valueOf(value));
                if (entry.getWinningSource() == null) {
                    entry.setWinningSource(source.getName());
                }
            }
            report.getEntries().add(entry);
        }

        for (String optionName : applicationArguments.getOptionNames()) {
            List<String> values = applicationArguments.getOptionValues(optionName);
            report.getCommandLineArgs().put(optionName,
                    values == null || values.isEmpty() ? "" : String.join(",", values));
        }
        return report;
    }

    /** 哨兵：表示该属性源里没有这个 key（区别于「有 key 但值为 null」） */
    private static final Object NOT_FOUND = new Object();

    /**
     * 读单个属性源，读不到就返回哨兵。
     * <p>
     * 逐个属性源取值的意义：把「被覆盖掉的那个值」也留在证据里，
     * 否则你只能看到赢家，没法解释为什么它赢了。
     */
    private Object readQuietly(PropertySource<?> source, String key) {
        try {
            if (!source.containsProperty(key)) {
                return NOT_FOUND;
            }
            return source.getProperty(key);
        } catch (Exception ex) {
            // 个别属性源（如随机值源）对任意 key 的查询会抛异常，忽略即可
            return NOT_FOUND;
        }
    }
}
