# 15 · 自动配置与自定义 Starter

> 模块：`basics-audit-spring-boot-starter/`（发布为普通 jar）+ `springboot-basics/`（8010，使用方）。
> 一句话：使用方 pom 里引一个依赖，什么都不写就能 `@Autowired AuditLogger`——本仓库亲手把这个魔法拆开。

## 学什么

**starter 的本质是「依赖即能力」**：`springboot-basics/pom.xml` 只加了一段
`basics-audit-spring-boot-starter` 依赖，该模块全程没有 `new AuditLogger()`、没有声明它的 `@Bean`，
却在 `audit/AuditAspect.java` 里直接构造器注入了它。装配细节全藏在 starter 的自动配置类里，
使用方只关心两件事：**我能配什么**（`basics.audit.*`）与**我能用什么**（`AuditLogger`）。

Spring Boot 官方把一个能力拆成 `xxx-spring-boot-starter`（依赖聚合）与 `xxx-spring-boot-autoconfigure`
（配置类 + `imports` 文件）两个 artifact：好处是使用方只依赖前者、实现可替换，配置类还能被别的 starter 复用；
代价是模块数量翻倍、教学链路变长。本仓库教学上合成一个模块——类少、`git log` 看清全部，代价是使用方依赖
到「实现」，升级要同时换两个 artifact 的版本。生产建议拆分，理解后拆分只是搬文件。

**两种注册方式并存**：

```
META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports  ← 2.7+ 推荐，只有一行类名
META-INF/spring.factories                                                         ← 旧写法，key 是 EnableAutoConfiguration
```

`spring.factories` 是 Boot 1.x~2.6 的唯一方式，2.7 兼容，**3.0 起彻底移除、只认 `imports`**——本仓库升级到
Boot 4.0.8 后自动配置注册只认 `imports` 文件，`spring.factories` 里那一行已不被读取（保留只为新旧对照）。
**两者同时写不会重复装配**：2.7 时代 Boot 会读两处并去重；Boot 4 只读 `imports`，`spring.factories` 写了也不生效。
迁移就三步：新建 `imports` → 删掉 `spring.factories` 里那一项 →（为兼容旧 Boot 客户）临时保留。

## 核心代码

### 条件装配：让自动配置在别人的环境里安全生效

自动配置类会被**每一个**引入该依赖的应用加载，它必须自己判断「该不该生效」：

```java
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuditProperties.class)
@ConditionalOnProperty(prefix = "basics.audit", name = "enabled",
        havingValue = "true", matchIfMissing = true)     // 不配也能用；配 false 一键关掉
public class AuditAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean                                // 使用方自定义同类型 Bean 就让位
    public AuditLogger auditLogger(AuditProperties properties) {
        log.info("basics-audit 自动配置生效：prefix={}, ...", properties.getPrefix());   // 故意留的「生效证据」
        return new AuditLogger(properties);
    }
}
```

| 条件注解 | 判断依据 | 本仓库/常见用途 |
| --- | --- | --- |
| `@ConditionalOnProperty` | 配置项的值（可设 `matchIfMissing`） | `basics.audit.enabled=false` 一键关闭审计 |
| `@ConditionalOnMissingBean` | 容器里是否已有该类型 Bean | **可覆盖**：使用方自定义 `AuditLogger` 即完全接管 |
| `@ConditionalOnClass` | classpath 上是否存在某类 | 本模块未用；如引入 Redis 才装配 Redis 相关 Bean（用 `name` 避免类加载失败） |
| `@ConditionalOnWebApplication` | 是否 Web 应用（`SERVLET`/`REACTIVE`） | 本模块未用；Web 相关 Filter/Controller 用它防止在批处理应用里报错 |
| `@ConditionalOnBean` | 容器内是否已有某 Bean | 更精细的开关，顺序敏感（见下文排序） |

`matchIfMissing = true` 是设计要点：使用方「什么都不配」时功能可用，才符合 starter 的直觉。条件注解就是
「**自动配置的兼容层**」：同一份 jar 进 Web 应用、批处理应用、测试切片都不能炸，靠这些注解逐条认领生效范围。

### 配置绑定：`@ConfigurationProperties` 四件套

```java
@ConfigurationProperties(prefix = "basics.audit")
public class AuditProperties {
    private boolean enabled = true;                        // 默认开启
    private String prefix = "[AUDIT]";                     // 默认值即「不配也能用」
    private int maxDetailLength = 200;
    private int bufferSize = 50;
    private String level = "INFO";
    private Duration slowThreshold = Duration.ofSeconds(1);
}
```

- **松散绑定**：yml 写 `max-detail-length`、`slow-threshold`，自动映射到 `maxDetailLength` / `slowThreshold`；
- **`Duration` 接单位**：`500ms` / `2s` / `1m` 都能绑，比 `long timeoutMs` + 口头约定单位安全得多
  （日志里打印成 ISO-8601 的 `PT0.5S`，见下方实测）；
- **默认值设计原则**：每个字段都要有合理默认，并把默认值写进 javadoc——它就是给别人看的文档；
- `@EnableConfigurationProperties(AuditProperties.class)` 把配置类注册成 Bean 并完成绑定；加了它就不必再给
  该类标 `@Component`（标了反而可能被组件扫描重复注册）；
- 配置元数据由 `spring-boot-configuration-processor` 编译期生成到
  `target/classes/META-INF/spring-configuration-metadata.json`，IDE 里敲 `basics.audit.` 才有补全与默认值提示。
  本 starter 把它标成 `<optional>true</optional>`：它是**编译期**工具，不该传递给使用方。

### 加载、排序与排查

- `@AutoConfiguration` 是 2.7 引入的「自动配置专用 `@Configuration`」，带 `proxyBeanMethods = false`
  语义并支持 `after`/`before`；本模块用等价的 `@Configuration(proxyBeanMethods = false)`，迁移只是一行改动；
- 排序用 `@AutoConfigureBefore` / `@AutoConfigureAfter`（写在自动配置类上），解决「我的 Bean 依赖别人的 Bean」；
  规则是**只在自动配置之间排序**，别指望它给业务 Bean 排序；
- **自动配置类不要加 `@ComponentScan`**：自动配置与组件扫描是两条独立通道，加扫描会把 starter 内部实现类
  无差别注册进使用方容器，且扫描进来的 Bean 不受条件注解约束，还会引发重复注册与循环依赖；
- `@Import` 与组件扫描的关系：自动配置类里的 `@Bean` 天然在容器里；能力类若在**别的包**，
  用 `@Import(SomeClass.class)` 精确引入比 `@ComponentScan` 安全得多；
- **契约边界**：starter 对外只暴露「配置项 + 能力接口」，内部实现类不进 API——否则使用方想覆盖时只能连实现一起抄。

排查「我的自动配置为什么没生效」：启动参数加 `debug=true`，控制台会打印 `ConditionEvaluationReport`
（`Positive matches` 找 `AuditAutoConfiguration`，`Negative matches` 看是哪个条件否了）；也可以用
`spring.autoconfigure.exclude=com.wkedong.boot.audit.AuditAutoConfiguration` 显式排除。本模块还把
`conditions` 暴露成了端点（`exposure.include` 里含 `conditions`），`GET /actuator/conditions` 不用重启
就能查同一份报告。三条经验：① 报告里**连类名都没出现** → `imports` 文件没打包进 jar（查 `META-INF/`
路径大小写与 `resources` 过滤）；② 出现在 Negative matches → 看条件描述，通常是 `enabled` 配成了非 `true` 的值；③ 出现在 Positive matches
但 Bean 不在 → 十有八九被**你自己的**同名 Bean 顶掉了。

## 动手验证

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
cd /Users/wkedong/Documents/wkdong/workspace/Spring-Cloud

# 1) 先装 starter（使用方要能解析到这个依赖）
mvn -B -pl basics-audit-spring-boot-starter -am install -DskipTests
# 2) 启动使用方（8010，零外部依赖），观察启动日志
mvn -B -pl springboot-basics spring-boot:run
# 3) 配置真的被 starter 读到了；4) 能力也被用起来了
curl -s -H 'X-Token: dev-token' http://127.0.0.1:8010/api/config | python3 -m json.tool
curl -s http://127.0.0.1:8010/actuator/basics
```

实测输出摘要（第 1、2 步本机 2026-10-08 运行；把启动输出重定向到文件即可对照，如 `> /tmp/basics-8010.log 2>&1`）：

```
[INFO] Copying 2 resources                          ← imports 文件与 spring.factories 都被打包
[INFO] basics-audit-spring-boot-starter ............. SUCCESS [  0.618 s]
[INFO] BUILD SUCCESS

14:43:10.134 INFO [main] c.w.b.audit.AuditAutoConfiguration
  - basics-audit 自动配置生效：prefix=[BASICS-DEV-AUDIT], maxDetailLength=120, slowThreshold=PT0.5S, bufferSize=50
14:43:10.607 INFO [main] c.w.s.b.SpringBootBasicsApplication - Started SpringBootBasicsApplication in 4.334 seconds
```

第二行就是**「自动配置真的跑了」的证据**，三个值都对得上使用方 yml：`prefix` 被 `application-dev.yml`
覆盖成 `[BASICS-DEV-AUDIT]`、`max-detail-length: 120`、`slow-threshold: 500ms` → Java 里是 `Duration`，
打印成 `PT0.5S`。

第 3、4 步实测片段（模块起来后按上面的命令执行即可复现）：

```json
"basicsAuditFromStarter": { "prefix": "[BASICS-DEV-AUDIT]", "maxDetailLength": 120,
    "slowThreshold": "PT0.5S", "level": "DEBUG" }
{"time":"14:43:11.400","action":"创建用户",
 "detail":"UserController.create(..) traceId=f87e136028374a28 成功","costMillis":30}
```

第一段直接来自 `AuditLogger.currentProperties()`，即 starter 内部持有的 `AuditProperties`——使用方没自己
解析过任何字段。第二段的链路是 `@AuditLog("创建用户")` → `AuditAspect` → starter 的
`AuditLogger.record(...)`：`action` 来自**使用方的注解值**，`traceId` 来自 MDC，`detail` 的截断长度由
**starter 的 `maxDetailLength=120`** 决定。两边各出一半，这正是 starter 该有的边界。

### 反证实验（两个都实测过）

**实验 A：关掉自动配置**——把 `basics.audit.enabled` 改成 `false` 后启动。实测（临时用一个开
`basics.audit.enabled=false` 的上下文验证，验证后临时文件已删除）：

```
APPLICATION FAILED TO START
Parameter 0 of constructor in ...BasicsEndpoint required a bean of type
'com.wkedong.boot.audit.AuditLogger' that could not be found.
Caused by: NoSuchBeanDefinitionException: No qualifying bean of type 'com.wkedong.boot.audit.AuditLogger' available
```

① 条件注解确实拦住了自动配置（`自动配置生效` 日志出现 **0 次**）；② **关掉开关会让所有依赖该能力的 Bean
启动失败**——生产里应在使用方加守卫（`@ConditionalOnBean(AuditLogger.class)`）或把能力做成可选的
（`ObjectProvider<AuditLogger>`）。

**实验 B：使用方自定义 `AuditLogger` Bean**——用 `@TestConfiguration` 定义同名 `@Bean`。实测：上下文启动
成功，注入到的是自定义实例，容器里该类型 Bean **只有 1 个**，且 starter 的 `auditLogger()` 工厂方法
**没执行**（`自动配置生效` 日志同样 0 次）。这就是 `@ConditionalOnMissingBean` 的语义：**先到先得，用户优先**
——使用方不改 starter 一行代码即可替换实现。

## 思考点

- **可覆盖是否真的可用**：若 `AuditLogger` 是 `final` 类或方法非 public，使用方想覆盖就得连实现一起抄。
  你会怎么设计 API，让「可覆盖」不只是注解上的一句话？
- **`@ConditionalOnMissingBean` 的时序陷阱**：它按 Bean 定义的**注册顺序**判断，使用方配置类若晚于自动配置
  被解析，让位就可能失效。`@AutoConfigureBefore/After` 与 `@ConditionalOnBean` 各自能解决什么、不能解决什么？
- **能力缺失时的降级**：实验 A 里关掉开关直接导致启动失败。改成 `ObjectProvider` + 空实现，还是把开关做成
  「不影响其它 Bean 创建」？两种方案对使用方的排错成本有什么差别？
- **配置项演进**：`basics.audit.level` 现在是 `String`，改成枚举后，旧配置写错的用户会在启动时炸掉
  还是静默走默认值？starter 作者该选哪种失败方式？
- **注册方式迁移**：若 starter 要同时支持 Boot 2.6 与 3.0 的使用方，两份注册文件都留有什么代价？
  `@AutoConfiguration` 相对 `@Configuration` 多的语义，值不值得现在就换？

> 延伸：自动配置在**测试切片**里会生效吗？见 [16-测试策略](16-测试策略.md)。
