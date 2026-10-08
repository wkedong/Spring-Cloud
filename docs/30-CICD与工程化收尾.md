# 30 · CI/CD 与工程化收尾

> 模块：全仓库（流水线配置 `.github/workflows/ci.yml`）｜前置阅读：09-本地运行指南、16-测试策略、29-容器化部署

## 学什么

前 29 篇讲「每个组件怎么写对」，这一篇讲**怎么保证它一直是写对的**。17 个 Maven 模块（reactor 共 18 行：根 pom + 15 个顶层目录 + `seata-demo` 的两个子模块），
一个改动可能同时踩中四类问题：

| 问题 | 典型症状 | CI 的价值 |
| --- | --- | --- |
| 编译基线漂移 | 本机默认 JDK 与流水线不一致时，同一份代码可能编不过或行为不同；当前 `java.version=17` 是硬基线（Boot 4 不再支持 8/11） | 统一 JDK（`java-version: '17'`）上编译期暴露 |
| 依赖版本冲突 | 同一个库被两个模块各引一个补丁版本，换了环境的运行时才炸 | BOM 统一 + 依赖树可查 |
| 多模块 reactor 顺序 | 没装 `basics-audit-spring-boot-starter`，`springboot-basics` 就找不到类 | 全量 reactor 一起编 |
| 文档/配置漂移 | yml 缩进、`@Disabled` 原因过期、README 端口与代码不一致 | 至少保证「能编、能跑测试」 |

CI 不是「多一道流程」，而是把检查**从人的记忆里搬到机器上**：本机命令一行不改地放进流水线，谁提交都一样跑。

## 核心代码

### 1. 触发条件：主分支推送 + 所有 PR

`.github/workflows/ci.yml` 里 `on.push.branches: [ master, develop ]` 加 `pull_request`：功能分支靠 PR 触发，避免每次提交都排队；同一分支连续推送用 `concurrency` 取消上一次未完成的运行。

### 2. build job：编译 + 打包 + 制品

```yaml
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: maven
      - run: mvn -B -V -DskipTests package
```

`-DskipTests` 只跳过**执行**，`test-compile` 照样跑——测试类写错一样会红；Temurin 17 与本机 Corretto 17 产出的字节码一致（都按 `java.version=17` 编译）。
打包后把 16 个 jar 路径显式列给 `actions/upload-artifact`，`if-no-files-found: error` 保证路径写错时立刻失败。

### 3. test job：只跑不依赖中间件的用例

```yaml
      - run: mvn -B -Dtest='*Test,*Tests' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test
```

**两个开关都要写**：`eureka`/`config`/`nacos-demo` 等 8 个模块没有测试源码，而一旦指定了 `-Dtest`，
surefire 在「一个匹配的测试类都没有」的模块上会直接判失败。Boot 4 托管的 **surefire 3.5.6** 把这件事拆成了两个参数
（旧写法只写 `-DfailIfNoTests=false` 已经拦不住，见下节易错点 1）：`-Dsurefire.failIfNoSpecifiedTests=false` 管「一个都没匹配上」，
`-DfailIfNoTests=false` 管「测试类在、但没有可执行用例」。这些模块的验证形态本来就是「起进程 + `curl`」
（见 docs/09、docs/16）；失败时用 `if: always()` 把 `**/target/surefire-reports/*.txt` 捞出来，不必只靠日志翻用例名。

### 4. quality job（可选）：依赖治理

```yaml
      - run: mvn -B -DskipTests verify
      - run: mvn -B -pl service-consumer-feign -DskipTests dependency:tree "-Dincludes=org.springframework.cloud:*,com.alibaba.cloud:*"
```

`verify` 把生命周期走到校验阶段，`dependency:tree` 的输出就是「BOM 到底给了哪个版本」的现场证据。
**不放进 CI** 的是集成测试：eureka(6060)/config(6010)/MySQL(3306)/Zipkin(9411)/Nacos(8848)/Kafka(9092)/
Seata(8091) 在托管 runner 上都没有，跑起来必红——长期红的流水线等于没有流水线。

### 5. 流水线的完整分层

| 层 | 命令 / 动作 | 本仓库现状 | 产物 |
| --- | --- | --- | --- |
| ① 编译 | `mvn -B -V -DskipTests package` | 17 个模块（reactor 18 行）全绿 | `target/classes` |
| ② 单元测试 | `mvn -B -Dtest='*Test,*Tests' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | 升级后复跑 BUILD SUCCESS、0 失败 0 错误（升级前存档：16 个用例执行、6 个 `@Disabled` 跳过） | `target/surefire-reports/` |
| ③ 打包 | 同 ①（`package` 阶段） | 16 个 jar（15 个可执行 + starter 库 jar） | `target/*.jar` |
| ④ 制品上传 | `actions/upload-artifact@v4` | 保留 7 天 | 可下载的 jar |
| ⑤ 镜像构建 | `java -Djarmode=layertools -jar app.jar extract` | 见 docs/29-容器化部署.md | `dependencies/`、`spring-boot-loader/`、`snapshot-dependencies/`、`application/` |
| ⑥ 部署 | 滚动 / 蓝绿 | 教学环境用 docs/09 的手工启动 | 新实例接管流量 |

第 ⑤ 层为什么要先 `extract`：Boot 4 的 fat jar 里带 `BOOT-INF/layers.idx`（本机已核实，旧版 Boot 2.7 同样如此），按层写 Dockerfile 后
**改一行业务代码只让 `application` 层失效**，依赖层命中缓存；本仓库 starter 是 SNAPSHOT、被归进 `application` 层，
所以 `snapshot-dependencies` 层是空的——这正是「SNAPSHOT 依赖破坏分层缓存」的活教材。部署层遵循「先扩后缩」：
新版本先起、健康检查过、再摘旧实例；蓝绿在此基础上多留一套环境用于秒级回滚。

### 6. 版本与分支策略

| 对象 | 角色 | 事实依据 |
| --- | --- | --- |
| `develop` | 教学主线，新内容先落这里 | `git log --oneline -5`：`68f44ff` 旧版退场叙述清理、`80ed8a3` 升级到 Boot 2.7.18 + Cloud 2021.0.9（最新一次「升级到 Boot 4.0.8 / Cloud 2025.1.3 / SCA 2025.1.0.0」的提交在它之上，哈希以本机 `git log` 为准） |
| `master` | 与 develop 对齐的对外稳定镜像 | 见 README「分支说明」 |
| tag `edgware-legacy` | 旧版完整快照（Boot 1.5.2 + Cloud Edgware.SR5） | `git show edgware-legacy` → 指向 `f52281f`（2019-02-27） |

追溯旧写法：`git show edgware-legacy:<路径>` 或 `git worktree add /tmp/legacy edgware-legacy`；差异清单见 docs/08-升级迁移指南.md。

### 7. 依赖治理：BOM 是唯一的版本出口

父 pom 用 `dependencyManagement` 导入三个 BOM（`spring-boot-starter-parent:4.0.8` +
`spring-cloud-dependencies:2025.1.3` + `spring-cloud-alibaba-dependencies:2025.1.0.0`），子模块的
`<dependency>` **一律不写 `version`**：写了就绕过 BOM，制造「同一个库两个补丁版本」的漂移。**例外也要写清理由**：
Boot 4 只带 Jackson 3，而 Seata 客户端仍用 Jackson 2，所以 `seata-demo` 里那一条 `jackson-databind` 必须显式写版本
（根 pom 的 `jackson2.version` 统一管理，boot BOM 已经不管它了）。排查三板斧（本机逐条验证过）：

```bash
mvn -B -pl service-consumer-feign dependency:tree -Dincludes=org.springframework.cloud:*    # 谁把版本带进来的
mvn -B -pl service-consumer-feign dependency:tree -Dverbose | grep -i "omitted for conflict" # 被仲裁掉的版本
mvn -B -N help:effective-pom | grep -A1 "spring-cloud-commons"                              # BOM 展开后的最终版本
```

CI 侧还有两件事：`cache: maven` 缓存本地仓库（命中后整轮免下载）；企业内网自建 Nexus 时把地址写进
CI 环境的 `~/.m2/settings.xml`（`<mirror>` 指向私服，凭据用仓库 Secrets 注入的环境变量，**不要把密码
写进 pom 或提交 settings.xml**）。本仓库教学环境直连中央仓库，不依赖私服。

### 8. 工程化收尾清单

| # | 项 | 本仓库落点 |
| --- | --- | --- |
| 1 | 日志规范（traceId、结构化） | docs/10、docs/14 |
| 2 | 统一异常与统一响应体 | docs/10（`ApiResponse` + 全局异常处理） |
| 3 | 配置外置与多环境 | docs/11、docs/24 |
| 4 | 可观测（Actuator / 指标 / 看板） | docs/14、docs/27（采集配置在 `deploy/`） |
| 5 | 安全（认证授权、JWT） | docs/22 |
| 6 | 文档即代码（与代码同仓、同 PR 评审） | 本仓库 `docs/` 与代码同提交 |

## 关键机制与易错点

1. **`-Dtest` + 无测试模块 = 失败，而且 surefire 3.x 换了参数名**：旧版（surefire 2.22）报错原文是
   `on project eureka: No tests were executed! (Set -DfailIfNoTests=false to ignore this error.)`，
   那时写一个 `-DfailIfNoTests=false` 就够了；升级到 Boot 4（托管 **surefire 3.5.6**）后，
   `-DfailIfNoTests=false` 只管「测试类存在但没有可执行用例」，而「一个匹配的测试类都没有」改由
   **`-Dsurefire.failIfNoSpecifiedTests=false`** 控制。所以 CI 里两个开关一起写才是全绿写法。
   只有「一个匹配的测试类都没有」才触发，模块有测试类但全被 `@Disabled` 只是 `Skipped`。
2. **`-DskipTests` ≠ `-Dmaven.test.skip=true`**：前者编译测试源码、不执行；后者连编译都跳过。
   CI 想「编译期发现测试代码错误」，靠的是前者。
3. **`-pl` 不带 `-am` 的坑**：`mvn -pl springboot-basics test` 在本地仓库没有 `basics-audit-spring-boot-starter:0.0.1-SNAPSHOT` 时会直接解析失败，要 `-am` 带上依赖模块。
4. **本机默认 JDK 是 11**：命令前必须 `export JAVA_HOME=$(/usr/libexec/java_home -v 17)`，
   否则同一条命令的报错会与 CI 不一致。
5. **`package` 前别习惯性加 `clean`——但改了依赖必须加**：平时的业务代码改动，教学机多人共享 `target/` 与运行中的进程，
   全量 `clean` 既拖慢自己也打断别人，增量即可；**但升级/换 starter/加依赖之后一定要 `clean package`**：
   Boot 4 的 `repackage` 不会刷新 fat jar 里已经打进去的嵌套依赖，只跑 `package` 会静默复用旧包
   （本机实测：新加的 jar 不会出现在 `BOOT-INF/lib`，排查起来像「代码没生效」）。
6. **制品路径写死版本号**：`0.0.1-SNAPSHOT` 来自父 pom，改版本要同步改流水线里的 16 条路径，否则 job 因缺制品变红——这是**故意的**，比静默通过好。

## 动手验证

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17); cd /Users/wkedong/Documents/wkdong/workspace/Spring-Cloud
mvn -B -v                                                            # 1) 工具链版本
mvn -B -q -DskipTests package                                        # 2) 增量打包（不加 clean；改了依赖才需要 clean，见易错点 5）
mvn -B -pl springboot-basics,basics-audit-spring-boot-starter test    # 3) 两个模块的测试
mvn -B -pl eureka -Dtest='*Test,*Tests' test                         # 4) 复现「无测试模块」的失败
mvn -B -Dtest='*Test,*Tests' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test   # 5) 全仓测试（CI 用的那条）
python3 -c "import yaml;yaml.safe_load(open('.github/workflows/ci.yml'))" && echo YAML OK   # 6) 校验流水线语法
```

**升级前实测输出摘要存档**（2026-10-08，Maven 3.9.16 + Corretto 17.0.20、Boot 2.7/surefire 2.22 时代）：

```
# 1) Apache Maven 3.9.16 / Java version: 17.0.20, vendor: Amazon.com Inc. / arch: aarch64
# 2) 退出码 0、耗时 7.065s，-q 级别下无任何 WARN/ERROR 输出
# 3) BUILD SUCCESS（10.269s）：basics-audit-spring-boot-starter SUCCESS [0.415s]（无测试源码，不失败）；
#    springboot-basics SUCCESS [8.841s]：
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 - in ...SpringBootBasicsApplicationTests
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0 - in ...web.UserControllerTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 - in ...service.TransferServiceTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 - in ...service.ProductServiceCacheTest
[INFO] Tests run: 15, Failures: 0, Errors: 0, Skipped: 0
# 4) Failed to execute goal ...maven-surefire-plugin:2.22.2:test (default-test) on project eureka:
#    No tests were executed! (Set -DfailIfNoTests=false to ignore this error.)
#    ↑ 这是旧版 surefire 的报错文本；surefire 3.5.6 下同一场景要加 -Dsurefire.failIfNoSpecifiedTests=false
```

**升级后实测**（Boot 4.0.8 / Spring Cloud 2025.1.3 / SCA 2025.1.0.0 的最终树）：

```text
# 全量构建：mvn -B -DskipTests clean package → BUILD SUCCESS（17 个模块）
# 全仓测试：mvn -B -Dtest='*Test,*Tests' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test
#          → BUILD SUCCESS（0 失败、0 错误）
# 校验脚本：scripts/verify-core.sh（核心链路 44 条断言）44/44 PASS；scripts/verify-basics.sh（Boot 本体 21 小节）0 失败
#          ——注意两者都要求先用 clean package 重新打包并全量重启进程，输出目录 /tmp/sc-upgrade/
```

流水线等价性验证：把仓库剔除 `target/` 复制成干净副本，在副本里逐条跑 `.github/workflows/ci.yml` 的 `run` 命令——
`mvn -B -V -DskipTests package` → `BUILD SUCCESS`（升级前存档：7.415s，产出 16 个 jar，16 条制品路径 16/16 命中）；
`mvn -B -Dtest='*Test,*Tests' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` → `BUILD SUCCESS`
（升级前存档：21.852s，网关上下文 1 个用例真跑、6 个 `@Disabled` 跳过、springboot-basics 15 个全绿）；
`mvn -B -DskipTests verify` → `BUILD SUCCESS`。
**必须说清楚**：GitHub 官方 runner 无法在本机执行，以上只是「同一条命令在本机等价环境跑通」，真正的 runner 行为
（actions 版本、缓存命中率、Secrets）要推到远端仓库后由第一次真实运行确认。

## 思考点

1. 6 个 `@Disabled` 的集成测试是「技术债」还是「正确取舍」？如果 CI 提供中间件容器，你会把它们放进同一个 job、并行 job，还是 nightly 定时任务？各自失败成本差在哪？
2. `-Dtest='*Test,*Tests'` 靠命名约定筛选用例：约定带来收益，也带来「名字没写对就跑不到」的风险。
   你会用 JUnit 5 的 `@Tag`，还是在插件 configuration 里显式写 `includes` 来替代它？
3. BOM 统一了版本，但「谁能改父 pom 的 BOM」本身是治理问题：某模块要单独升级一个库时，允许 `<version>`
   覆盖，还是要求先升 BOM？两种做法的代价是什么？
4. 分层镜像的缓存收益依赖「依赖不变」，本仓库的 SNAPSHOT starter 就破坏了它。把 starter 拆成稳定版本发布，
   构建与部署复杂度会增加多少？值不值？如果把流水线当成代码，它自己的测试又该是什么？
