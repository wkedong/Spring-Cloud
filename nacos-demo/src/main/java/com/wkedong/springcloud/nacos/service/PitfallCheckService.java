package com.wkedong.springcloud.nacos.service;

import com.alibaba.cloud.nacos.NacosConfigProperties;
import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import org.springframework.cloud.util.PropertyUtils;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Service;
import org.springframework.util.ClassUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 踩坑自检：把「文档里说的坑」变成运行时能验证的事实。
 * <p>
 * 为什么值得单独一个 service：Nacos 的坑大多不是代码错误，而是
 * 「某个加载阶段没生效」——比如 bootstrap.yml 压根没被读、
 * 命名空间填了名字而不是 ID。这些都只能靠运行时自证，
 * 光看 yml 看不出来。本类把判断依据全部换成可观测事实：
 * <ul>
 *   <li>bootstrap 是否生效 → marker 类是否在 classpath（starter-bootstrap 里只有一个 Marker 类）；</li>
 *   <li>bootstrap.yml 是否被读 → 只在该文件出现的 key 是否为 null；</li>
 *   <li>import 检查是否会被触发 → 三个开关的取值照抄 SAA 的判断条件；</li>
 *   <li>namespace/group/dataId → 直接问 {@code NacosConfigProperties} 绑定到了什么。</li>
 * </ul>
 *
 * @author wkedong
 */
@Service
public class PitfallCheckService {

    /** starter-bootstrap 提供的标记类：它存在 = 引导上下文会被创建 */
    private static final String BOOTSTRAP_MARKER_CLASS = "org.springframework.cloud.bootstrap.marker.Marker";

    private final ConfigurableEnvironment environment;
    private final NacosConfigProperties nacosConfigProperties;
    private final NacosDiscoveryProperties nacosDiscoveryProperties;

    public PitfallCheckService(ConfigurableEnvironment environment,
                               NacosConfigProperties nacosConfigProperties,
                               NacosDiscoveryProperties nacosDiscoveryProperties) {
        this.environment = environment;
        this.nacosConfigProperties = nacosConfigProperties;
        this.nacosDiscoveryProperties = nacosDiscoveryProperties;
    }

    /**
     * 逐项自检，返回可直接对照文档阅读的结构化结果。
     *
     * @return 分段的检查结果
     */
    public Map<String, Object> checklist() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("bootstrap（Boot 2.4+ 不再默认加载 bootstrap.yml）", bootstrapCheck());
        result.put("config-import 检查（Spring Cloud 2020+）", importCheck());
        result.put("命名空间 / 分组 / dataId", coordinateCheck());
        result.put("松散绑定（@Value vs @ConfigurationProperties）", relaxedBindingCheck());
        result.put("Eureka+Config → Nacos 字段对照", eurekaConfigMapping());
        return result;
    }

    /**
     * 松散绑定自检：同一个配置项，两种写法在环境里的可见性。
     * <p>
     * 实测结论（本机）：Nacos 里写 {@code nacos.demo.profile-value} 时，
     * 用 {@code nacos.demo.profileValue} 去问环境是拿不到的；
     * {@code @ConfigurationProperties} 能绑上是因为它自己做了名字归一化，
     * 而 {@code @Value("${...}")} 是逐字查找——这里就是「配置读了但没生效」的常见根因。
     */
    private Map<String, Object> relaxedBindingCheck() {
        Map<String, Object> check = new LinkedHashMap<String, Object>();
        check.put("原始写法 nacos.demo.profile-value", environment.getProperty("nacos.demo.profile-value"));
        check.put("驼峰写法 nacos.demo.profileValue", environment.getProperty("nacos.demo.profileValue"));
        check.put("下划线写法 nacos.demo.profile_value", environment.getProperty("nacos.demo.profile_value"));
        check.put("大写环境变量写法 NACOS_DEMO_PROFILE_VALUE", environment.getProperty("NACOS_DEMO_PROFILE_VALUE"));
        check.put("结论", "Nacos 里的 key 请统一用 kebab-case，并在 @Value 里逐字照抄；"
                + "@ConfigurationProperties 可以写驼峰字段名（bind 阶段做归一化），@Value 不行");
        return check;
    }

    /**
     * bootstrap 是否真的生效，以及 profile 是否在引导期可见。
     */
    private Map<String, Object> bootstrapCheck() {
        Map<String, Object> check = new LinkedHashMap<String, Object>();
        boolean markerPresent = ClassUtils.isPresent(BOOTSTRAP_MARKER_CLASS, null);
        check.put("markerClassPresent", markerPresent);
        check.put("markerClass", BOOTSTRAP_MARKER_CLASS);
        check.put("bootstrapEnabled（PropertyUtils 判定）", PropertyUtils.bootstrapEnabled(environment));
        check.put("spring.cloud.bootstrap.enabled", environment.getProperty("spring.cloud.bootstrap.enabled"));
        // 这个 key 只写在 bootstrap.yml 的 discovery.metadata 里，命令行不会提供它：
        // 它非空即证明 bootstrap.yml 真的被读到了（比看 file-extension 更可靠，
        // 因为 file-extension 也可能由命令行传入而「假阳性」）
        String bootstrapOnlyKey = "spring.cloud.nacos.discovery.metadata.from";
        boolean bootstrapYmlLoaded = environment.getProperty(bootstrapOnlyKey) != null;
        check.put("bootstrapYmlLoaded（依据 bootstrap.yml 独有的 " + bootstrapOnlyKey + "）", bootstrapYmlLoaded);
        check.put("spring.profiles.active", Arrays.toString(environment.getActiveProfiles()));
        check.put("结论", markerPresent && bootstrapYmlLoaded
                ? "bootstrap 生效：starter-bootstrap 的 Marker 类让引导上下文被创建，bootstrap.yml 已加载"
                + "（注意：Marker 类存在时 PropertyUtils.bootstrapEnabled 恒为 true，"
                + "所以 --spring.cloud.bootstrap.enabled=false 并不能关掉 bootstrap——要真正关掉只能移除该 starter）"
                : "bootstrap 未生效：bootstrap.yml 会被忽略，Nacos 坐标只能靠 application.yml 或命令行提供");
        return check;
    }

    /**
     * Spring Cloud 2020+ 的 config import 检查：三个条件同时成立才会拦启动。
     */
    private Map<String, Object> importCheck() {
        Map<String, Object> check = new LinkedHashMap<String, Object>();
        boolean bootstrapEnabled = PropertyUtils.bootstrapEnabled(environment);
        boolean legacyProcessing = PropertyUtils.useLegacyProcessing(environment);
        boolean nacosConfigEnabled = Boolean.TRUE.equals(
                environment.getProperty("spring.cloud.nacos.config.enabled", Boolean.class, Boolean.TRUE));
        boolean importCheckEnabled = Boolean.TRUE.equals(
                environment.getProperty("spring.cloud.nacos.config.import-check.enabled", Boolean.class, Boolean.TRUE));
        check.put("bootstrapEnabled", bootstrapEnabled);
        check.put("useLegacyProcessing", legacyProcessing);
        check.put("spring.cloud.nacos.config.enabled", nacosConfigEnabled);
        check.put("spring.cloud.nacos.config.import-check.enabled", importCheckEnabled);
        check.put("spring.config.import", environment.getProperty("spring.config.import"));
        boolean wouldFail = !bootstrapEnabled && !legacyProcessing && nacosConfigEnabled && importCheckEnabled
                && environment.getProperty("spring.config.import") == null;
        check.put("当前配置会不会触发启动失败", wouldFail);
        check.put("触发条件", "非 bootstrap + 非 legacy + nacos.config.enabled + import-check.enabled + 未写 spring.config.import");
        check.put("修复方式", "① 加 spring-cloud-starter-bootstrap（本模块做法）；"
                + "② 或 spring.config.import=optional:nacos:nacos-demo.properties；"
                + "③ 或 spring.cloud.nacos.config.import-check.enabled=false（仅关闭检查，不加载配置）");
        return check;
    }

    /**
     * 坐标自检：namespace / group / 三个 dataId 的实际取值。
     */
    private Map<String, Object> coordinateCheck() {
        Map<String, Object> check = new LinkedHashMap<String, Object>();
        String namespace = nacosConfigProperties.getNamespace();
        check.put("config.serverAddr", nacosConfigProperties.getServerAddr());
        check.put("config.namespace（空串 = public）", namespace == null ? "" : namespace);
        check.put("config.group", nacosConfigProperties.getGroup());
        check.put("config.prefix（NacosConfigProperties 自带兜底逻辑，主上下文里 environment 为空时会返回 null）",
                nacosConfigProperties.getPrefix());
        check.put("config.fileExtension", nacosConfigProperties.getFileExtension());
        check.put("config.refreshEnabled", nacosConfigProperties.isRefreshEnabled());

        // dataId 前缀的兜底规则与 NacosPropertySourceLocator 一致：prefix → name → spring.application.name。
        // 直接拿 getPrefix() 可能得到 null（主上下文里的实例没有 environment），所以这里显式补齐。
        String prefix = nacosConfigProperties.getPrefix();
        if (!org.springframework.util.StringUtils.hasText(prefix)) {
            prefix = nacosConfigProperties.getName();
        }
        if (!org.springframework.util.StringUtils.hasText(prefix)) {
            prefix = environment.getProperty("spring.application.name");
        }
        String extension = nacosConfigProperties.getFileExtension();
        check.put("实际参与计算的 dataId 前缀", prefix);

        List<String> resolvedDataIds = new ArrayList<String>();
        resolvedDataIds.add(prefix + "." + extension);
        for (String profile : environment.getActiveProfiles()) {
            resolvedDataIds.add(prefix + "-" + profile + "." + extension + "（带 profile，优先级最高）");
            resolvedDataIds.add(prefix + "." + profile + "." + extension + "（点号形式，仅在服务端存在该 dataId 时才出现）");
        }
        if (nacosConfigProperties.getSharedConfigs() != null) {
            for (NacosConfigProperties.Config config : nacosConfigProperties.getSharedConfigs()) {
                resolvedDataIds.add(config.getDataId() + "（shared-configs，refresh=" + config.isRefresh() + "，优先级最低）");
            }
        }
        check.put("候选 dataId", resolvedDataIds);
        check.put("实际拉到的 dataId（环境里的属性源名）", loadedDataIdNames());

        check.put("discovery.serverAddr", nacosDiscoveryProperties.getServerAddr());
        check.put("discovery.namespace", nacosDiscoveryProperties.getNamespace());
        check.put("discovery.group", nacosDiscoveryProperties.getGroup());
        check.put("discovery.service（默认取的 spring.application.name）", nacosDiscoveryProperties.getService());
        check.put("discovery.metadata", nacosDiscoveryProperties.getMetadata());
        check.put("namespace 提醒", "控制台里显示的是命名空间「名称」，API/配置里要填命名空间「ID」；"
                + "public 的 ID 就是空串，所以默认不写即可");
        return check;
    }

    /**
     * 环境里真实存在的 Nacos 属性源，即「实际拉到的 dataId」。
     * <p>
     * 名字规则交给 {@link NacosPropertySources}：bootstrap 模式是
     * {@code bootstrapProperties-<dataId>,<group>}；config-import 模式是 {@code <group>@<dataId>}。
     */
    private List<String> loadedDataIdNames() {
        List<String> names = new ArrayList<String>();
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (source instanceof CompositePropertySource && "NACOS".equals(source.getName())) {
                for (PropertySource<?> child : ((CompositePropertySource) source).getPropertySources()) {
                    names.add(NacosPropertySources.toDataIdLabel(child.getName()));
                }
                continue;
            }
            if (NacosPropertySources.isNacosSource(source.getName())) {
                names.add(NacosPropertySources.toDataIdLabel(source.getName()));
            }
        }
        return names;
    }

    /**
     * 与仓库里 eureka/ + config/ 模块的字段对照（那些模块是 Edgware 迁移过来的对照物）。
     */
    private List<Map<String, String>> eurekaConfigMapping() {
        List<Map<String, String>> mappings = new ArrayList<Map<String, String>>();
        mappings.add(mapping("eureka.client.serviceUrl.defaultZone=http://localhost:6060/eureka/",
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848",
                "注册中心地址：Eureka 是 URL（自带协议与路径），Nacos 是 host:port"));
        mappings.add(mapping("eureka.instance.hostname / prefer-ip-address",
                "spring.cloud.nacos.discovery.ip / metadata",
                "实例地址来源：Nacos 默认取网卡 IP，可用 ip 显式指定"));
        mappings.add(mapping("（无对应项，Eureka 无命名空间）",
                "spring.cloud.nacos.discovery.namespace / group",
                "隔离模型：Nacos 用 namespace + group 两级隔离，Eureka 只能靠多套注册中心"));
        mappings.add(mapping("spring.cloud.config.uri=http://localhost:6010/ + label/profile/name",
                "spring.cloud.nacos.config.server-addr + dataId(prefix[-profile].ext) / group",
                "配置定位：Config 用 application/profile/label 三元组，Nacos 用 dataId + group + namespace"));
        mappings.add(mapping("（无对应项，改完要 /actuator/refresh 或重启）",
                "spring.cloud.nacos.config.refresh-enabled=true + @RefreshScope",
                "刷新机制：Nacos 客户端长轮询推送，配置变更即自动刷新"));
        mappings.add(mapping("spring-cloud-starter-netflix-eureka-client",
                "spring-cloud-starter-alibaba-nacos-discovery + -config",
                "依赖：Nacos 一个客户端同时承担注册与配置两件事"));
        return mappings;
    }

    private Map<String, String> mapping(String eureka, String nacos, String note) {
        Map<String, String> item = new LinkedHashMap<String, String>();
        item.put("eureka+config", eureka);
        item.put("nacos", nacos);
        item.put("说明", note);
        return item;
    }
}
