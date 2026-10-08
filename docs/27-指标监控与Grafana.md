# 27 · 指标监控与 Grafana（Micrometer + Prometheus）

> 模块：`springboot-basics/`（18010）+ `deploy/`（抓取与仪表盘部署物）。实测环境：Boot 2.7.18 /
> Micrometer 1.9.17 / Prometheus 3.15.0 / Grafana 13.2.3（macOS，无 Docker）；自建 19090/19300，共享 9090/3000 未动。

## 学什么

`docs/10`、`docs/14` 讲的是一个请求进来怎么被处理、怎么被记下来。到了线上「记下来」不够用：
QPS 多少？P95 多少？堆内存是不是要满了？这类问题需要**可聚合、可告警、成本可控**的数据。

| 数据 | 回答什么问题 | 成本 | 保留期 | 典型工具 |
| --- | --- | --- | --- | --- |
| 指标 Metrics | 「现在/趋势如何」，可聚合可告警 | 极低（预聚合的数字） | 月~年 | Prometheus + Grafana |
| 日志 Logs | 「到底发生了什么」，单条事件细节 | 高（每请求数行） | 天~周 | ELK / Loki（见 `docs/28`） |
| 追踪 Traces | 「这一次请求慢在哪一跳」 | 中（采样） | 天 | Zipkin（见 `docs/07`） |

三者不是互相替代（**指标发现问题 → 追踪定位环节 → 日志看清细节**），本篇先打通最便宜的那层：

```
业务代码（零埋点） → Micrometer 门面 → Prometheus 注册表 → /actuator/prometheus（文本）
     → Prometheus 定时拉取（pull）→ 时序库 → Grafana 面板 / 告警规则
```

排查也别对着两百个面板乱看：**RED**（服务视角）看 Rate 请求量 / Errors 错误 / Duration 时延，对应本篇的
`http_server_requests_seconds_count`（按 `uri`、`status` 分组）；**USE**（资源视角）看 Utilization 使用率 /
Saturation 饱和度 / Errors，对应 `jvm_memory_used_bytes`、`hikaricp_connections_pending`。

## 核心代码

### 1. 加依赖：只加注册表，不改业务代码

```xml
<!-- springboot-basics/pom.xml：版本由 spring-boot-dependencies 管理，不必写 version -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

本仓库 10 个模块（basics / producer / 4 个消费者 / zuul / nacos / sentinel / stream / security）加的是同一份
依赖，**业务代码一行没改**——这是门面（Facade）模式的价值。

### 2. 加配置：暴露端点 + 通用标签 + 直方图

```yaml
management:
  endpoints.web.exposure.include: health,info,metrics,prometheus,...  # 不加 prometheus 就是 404
  metrics:
    tags.application: ${spring.application.name}                     # 通用标签：所有指标自带服务名
    distribution.percentiles-histogram.http.server.requests: true     # 开直方图才能算 P95/P99
```

### 3. 两个端点，两种格式（别把调试端点当抓取端点）

`/actuator/metrics` 给人看，是 JSON：

```json
{"name":"http.server.requests","baseUnit":"seconds",
 "measurements":[{"statistic":"COUNT","value":25.0},{"statistic":"MAX","value":5.009027333}]}
```

`/actuator/prometheus` 给机器抓，是**文本 exposition 格式**（一行一个样本）：

```
http_server_requests_seconds_count{application="springboot-basics",method="GET",outcome="SUCCESS",status="200",uri="/api/public/ping",} 5.0
jvm_memory_used_bytes{application="springboot-basics",area="heap",id="G1 Eden Space",} 3.3554432E7
cache_gets_total{application="springboot-basics",cache="products",result="hit",} 4.0
```

### 4. 抓取配置（`deploy/prometheus.yml`）

```yaml
scrape_configs:
  - job_name: 'spring-cloud-apps'
    metrics_path: '/actuator/prometheus'      # 不是 /actuator/metrics
    scrape_interval: 10s                      # 覆盖全局 15s
    static_configs:                                             # target 标签：实例挂了也还在
      - { targets: ['127.0.0.1:18010'], labels: { application: 'springboot-basics' } }
    metric_relabel_configs: [{ action: labeldrop, regex: 'exported_application' }]
```

## 关键机制

**1. 门面与实现解耦。** 业务侧只依赖 `MeterRegistry` 抽象，换 Datadog/OTLP 只换依赖；
`http.server.requests`、`jvm.memory.used` 这些**名字与标签是 Micrometer 定的规范**，仪表盘可跨实现复用。

**2. 命名可解析：`http_server_requests_seconds_count` = 名字 + 单位 + 聚合后缀。** 点号在 exposition
里变下划线，单位 `seconds` 由 Micrometer 按 `baseUnit` 补上（避免「秒还是毫秒」的经典事故），后缀是聚合
形态：Timer 同时给出 `_count`/`_sum`/`_max`/`_bucket`，所以平均耗时是 `rate(_sum)/rate(_count)`，
**别直接读 `_sum`**。

**3. pull 模型与 exposition 格式。** 应用只把当前快照写成文本、Prometheus 主动来拉：应用无状态、目标列表
在服务端、抓不到就 `up=0`（天然可告警）；代价是短命任务抓不到（需 Pushgateway）、精度上限由抓取间隔决定。

**4. 标签基数（cardinality）是唯一会「用监控搞垮数据库」的坑。** 「指标名 + 一组标签值」= 一条时间线，
把 userId、orderId、原始 URL 打成标签，时间线会随流量线性膨胀。本模块的 URI 标签是 `/api/users/{id}`
这种**模板**（Micrometer 取 handler mapping 的模式，而非请求原始路径）：实测同一接口打 46863 次仍只占一条
时间线。高危标签：用户 ID、订单号、traceId、异常 message；控制顺序：① 不打（首选）② 模板化
③ `metric_relabel_configs` drop/labeldrop ④ 明细留在日志里。

**5. 分位数要用直方图，不要用客户端分位数。** `percentiles: 0.95` 是各实例**自己算完再上报**，多实例
无法合并（P95 的平均不是平均的 P95）；`percentiles-histogram: true` 上报 `_bucket` 累积计数，
`histogram_quantile(0.95, sum by (le) (rate(..._bucket[5m])))` 可跨实例聚合。两个注意点：`by (le)` 必须在
**内层**、`histogram_quantile` 必须作用在 `rate()` 之后；窗口内样本太少会返回 `nan`（实测只请求过一次的
接口 P95 就是 `nan`）。

**6. target 标签 vs 指标标签。** 同名时以指标自带的为准，冲突的目标标签被改名为 `exported_application`
（实测查询结果里两者同时出现），这就是 `honor_labels` 默认 `false` 的含义；`labeldrop` 丢掉后 series 更省、
语义不变。

**7. 告警规则思路：看板求全，告警求少而准。** 每条规则都要能回答「收到它我该做什么」：

| 规则 | 表达式要点 | 说明 |
| --- | --- | --- |
| 实例失联 | `up{job="spring-cloud-apps"} == 0` + `for: 1m` | 最该有的第一条，`for` 滤抖动 |
| 错误率 | `sum(rate(...{outcome=~"CLIENT_ERROR\|SERVER_ERROR"}[5m])) / sum(rate(...[5m])) > 0.05` | 分母 `clamp_min` 防 0 除 |
| 时延劣化 | `histogram_quantile(0.95, sum by (le) (rate(..._bucket[5m]))) > 1` | 配合 SLO 桶省存储 |
| 池饥饿 | `hikaricp_connections_pending > 0` + `for: 2m` | 先查慢 SQL，再谈扩池 |

## 动手验证

```bash
# ① 起实例（自带 H2，无外部依赖）：实测 3.2s 就绪，PID 82189；日志里
#    Tomcat started on port(s): 18010 (http) / Started SpringBootBasicsApplication in 2.6 seconds
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
java -jar springboot-basics/target/springboot-basics-0.0.1-SNAPSHOT.jar --server.port=18010 &

# ② 先打业务流量（否则 http 指标是空的；无 token 的请求走 401，正是要观察的分支）
curl -s -o /dev/null -w '%{http_code} ' http://127.0.0.1:18010/api/public/ping
curl -s -o /dev/null -w '%{http_code}\n' -H 'X-Token: dev-token' http://127.0.0.1:18010/api/cache/products/1
# 实测 21 次请求：200×17、401×2（无 token）、400×1（业务异常）、404×1（用户不存在）

# ③ 抓取端点：HTTP 200、145995 字节、70 个指标家族（代表性行见上「核心代码 3」）
curl -s http://127.0.0.1:18010/actuator/prometheus | grep -c '^# TYPE'          # 70

# ④ 调试端点 + 按标签下钻（tag 语法 `tag=值`）
curl -s 'http://127.0.0.1:18010/actuator/metrics/http.server.requests?tag=uri:/api/public/ping&tag=status:200'
# "measurements":[{"statistic":"COUNT","value":5.0},{"statistic":"TOTAL_TIME","value":0.020578082}, ...]
curl -s 'http://127.0.0.1:18010/actuator/metrics/jvm.memory.used?tag=area:heap'
# "measurements":[{"statistic":"VALUE","value":68673344.0}]      ← 堆内存 65.5 MiB

# ⑤ 校验并启动自己的 Prometheus（共享实例在 9090，教学实例用 19090）
promtool check config deploy/prometheus.yml
#  SUCCESS: deploy/prometheus.yml is valid prometheus config file syntax
prometheus --config.file="$PWD/deploy/prometheus.yml" --storage.tsdb.path=/tmp/sc-prom-data \
           --web.listen-address=127.0.0.1:19090 &

# ⑥ 目标 UP（curl 原文，仅挑 springboot-basics 这条）
curl -s http://127.0.0.1:19090/api/v1/targets
# "scrapeUrl":"http://127.0.0.1:18010/actuator/prometheus", "lastError":"", "health":"up",
# "lastScrape":"2026-10-08T15:26:24.104388+08:00", "lastScrapeDuration":0.012588959"

# ⑦ up 与聚合（实测 14 个 target：4 UP / 10 DOWN，DOWN 的 lastError 多为 404 而非 refused）
curl -s 'http://127.0.0.1:19090/api/v1/query?query=up'
# up{application="springboot-basics", instance="127.0.0.1:18010"} 1
# up{application="service-producer", instance="127.0.0.1:6070"} 0   ← lastError: HTTP status 404
curl -s --data-urlencode 'query=count(up == 1)' http://127.0.0.1:19090/api/v1/query
# {"status":"success","data":{"resultType":"vector","result":[{"metric":{},"value":[1791444624.047,"4"]}]}}

# ⑧ 查业务指标（labeldrop 之后已无 exported_application）
curl -s --data-urlencode 'query=http_server_requests_seconds_count{application="springboot-basics"}' \
     http://127.0.0.1:19090/api/v1/query
# {"metric":{"application":"springboot-basics","method":"GET","outcome":"SUCCESS","status":"200",
#   "uri":"/api/public/ping"},"value":[1791444624.047,"25"]}；同批还有 status="401"、uri="/api/users" → 4

# ⑨ RED 三件套与缓存（rate 至少要两个样本，刚启动时为空）
curl -s --data-urlencode 'query=sum by (uri,status) (rate(http_server_requests_seconds_count{application="springboot-basics"}[2m]))' http://127.0.0.1:19090/api/v1/query
# /api/public/ping status=200 → 0.1162 req/s ；/api/users status=401 → 0.0194 req/s
curl -s --data-urlencode 'query=histogram_quantile(0.95, sum by (le, uri) (rate(http_server_requests_seconds_bucket{application="springboot-basics"}[5m])))' http://127.0.0.1:19090/api/v1/query
# /api/demo/slow → 5.655 s（故意 sleep 5s）、/api/cache/products/{id} → 0.8724 s（首次回源）、
# /api/public/ping → 0.0036 s、/api/demo/business-error → nan（窗口内样本太少）
curl -s --data-urlencode 'query=sum by (cache,result) (rate(cache_gets_total{application="springboot-basics"}[5m]))' http://127.0.0.1:19090/api/v1/query
# products hit 0.0310 /s、products miss 0.0077 /s → 命中率 80%

# ⑩ Grafana：provisioning 生效 + 端到端取到数（自建 19300，共享 3000 未动）
curl -s -u admin:admin http://127.0.0.1:19300/api/datasources
# [{"uid":"prometheus","name":"Prometheus","type":"prometheus","url":"http://127.0.0.1:19090","readOnly":true}]
curl -s -u admin:admin 'http://127.0.0.1:19300/api/search?type=dash-db'
# [{"uid":"sc-micrometer-overview","folderTitle":"Spring Cloud 教学","type":"dash-db", ...}]
curl -s -u admin:admin --get --data-urlencode 'query=up{application="springboot-basics"}' \
  'http://127.0.0.1:19300/api/datasources/proxy/uid/prometheus/api/v1/query'
# {"status":"success", ... "metric":{"__name__":"up","application":"springboot-basics", ...},"value":[...,"1"]}

# ⑪ 交付物合法性：仪表盘 JSON + provisioning/prometheus YAML；14 个面板、24 条查询实跑 OK=24 FAIL=0
python3 -m json.tool deploy/grafana/dashboards/spring-cloud-micrometer-overview.json > /dev/null && echo JSON_OK
python3 -c "import yaml; yaml.safe_load(open('deploy/prometheus.yml',encoding='utf-8')); print('YAML_OK')"
```

## 思考点

1. `http_server_requests_seconds_count` 只增不减、进程重启还会归零，监控系统凭什么仍能算出准确 QPS？
   Prometheus 内部怎么处理这种「counter 归零」？
2. 某个接口的 `uri` 标签如果打成了原始路径（`/api/users/9999`），一天后会付出什么代价？除了改代码，
   `metric_relabel_configs` 在采集侧能补救到什么程度、又补救不了什么？
3. 客户端分位数与直方图 + `histogram_quantile` 各适合什么场景？为什么三台机器的 P95 不能求平均？
4. 给「支付接口 P95 > 1s」配告警：窗口取 5m 还是 1h、`for` 取多久？窗口太短/太长分别带来哪类误报漏报？
   （提示：`up == 0` 那种「立刻要知道」的告警是例外）
5. 指标留 15 天、日志留 7 天、追踪采样 1%，这个组合在「半年后复盘一次事故」时会缺什么？预算只够加一项，
   你加哪一项、依据是什么？
