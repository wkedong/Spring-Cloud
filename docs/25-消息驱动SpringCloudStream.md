# 25 · 消息驱动微服务：Spring Cloud Stream

> 模块：`stream-demo/`（8230）。需要本地 Kafka（见「动手验证」）。
> 实测环境：Spring Cloud Stream 3.2.10 + spring-kafka 2.8.11 + kafka-clients 3.1.2，本机 Kafka 4.3.1（KRaft 单节点，127.0.0.1:9092）。
> 下文标「实测」的结论都来自本机跑出来的输出（见「动手验证」），标「原理」的只做说明、未在本机复现。

## 学什么

03~05 篇的同步调用把可用性绑在一起：RestTemplate/Feign 发出去就等，下游慢则上游线程被占，下游挂则上游跟着挂（05 篇用熔断兜底）。
消息驱动把「调用」换成「事件」：生产者只管投出去，消费者按自己的节奏处理，于是能解耦、削峰、最终一致；
代价是链路变异步——「发了」不再等于「处理了」，可观测性与幂等得自己补。

直接用 Kafka 客户端（`KafkaProducer`/`KafkaConsumer`）的问题不是难写，而是**把中间件 API 焊进业务代码**：换 RabbitMQ 要重写收发逻辑，
topic、分区、序列化、重试、DLQ 全归业务管。Spring Cloud Stream 的解法是 **Binder 抽象**：

1. 业务侧只写函数式 Bean：`Consumer<T>`（消费）、`Function<T,R>`（处理并转发）、`Supplier<T>`（定时生产）；
2. 框架按 **方法名** 生成 binding，再由 Binder 把 binding 翻译成中间件资源（Kafka = topic + consumer group；RabbitMQ = exchange + queue）；
3. 换中间件 = 换依赖 + 改 binder 配置，业务代码一行不动。

3.x 已废弃 2.x 的 `@EnableBinding`/`@StreamListener`（binding 集合在代码里写死、与 Spring Integration 深度耦合），
改为函数式模型：Bean 就是函数，**哪些函数参与绑定由 `spring.cloud.function.definition` 决定**。

## 核心代码

函数式三件套（`config/StreamFunctionConfig.java`）：

```java
@Bean
public Consumer<Message<String>> consume(MessageBuffer buffer) {          // → binding consume-in-0
    return message -> {
        String payload = message.getPayload();
        Map<String, String> kafkaHeaders = extractKafkaHeaders(message);  // kafka_receivedTopic / kafka_offset ...
        if (payload.toLowerCase().contains("fail")) {                     // 毒消息：故意抛异常，触发重试与 DLQ
            buffer.recordFailure(payload, "命中毒消息关键字");
            throw new IllegalStateException("模拟消费失败（毒消息）：" + payload);
        }
        buffer.record(payload, kafkaHeaders.get("kafka_receivedTopic"), ...);
    };
}
@Bean
public Function<String, String> transform() { return p -> "TRANSFORMED(" + p.toUpperCase() + ")"; } // -in-0 / -out-0
@Bean
public Supplier<String> produce() {                                   // produce-out-0，poller 按 fixed-delay 调用
    AtomicLong counter = new AtomicLong(0);
    return () -> "supplier-heartbeat-" + counter.incrementAndGet();
}
@Bean
public Function<String, String> audit() { return p -> "[audit]" + p; } // 不写进 definition：有 Bean ≠ 有 binding
```

HTTP 触发生产用 `StreamBridge`（生产环境最常见的「按需发送」；`web/StreamController.java`）：

```java
streamBridge.send("streamSend-out-0", content);                          // 纯文本
streamBridge.send("streamSend-out-0", MessageBuilder.withPayload(content)
        .setHeader(KafkaHeaders.MESSAGE_KEY, key.getBytes(UTF_8)).build());   // 带 key：相同 key 必进同一分区
```

| 端点 | 作用 |
| --- | --- |
| `POST /stream/send`（body=内容，可选 `?key=`） | 发送消息，返回 binding/destination/耗时 |
| `GET /stream/messages?limit=10` | 最近 N 条消费记录（topic/分区/offset/消费线程名/全部 `kafka_*` 原始头） |
| `GET /stream/stats` | 消费计数、失败尝试时间线、最后一条、消费线程名、当前 group 与 DLQ |
| `POST /stream/reset` | 清空本实例内存计数（多实例实验前必做） |

配置要点摘录（`application.yml` 里每一条都写了「为什么」，这里省掉注释与次要项）：

```yaml
spring:
  cloud:
    function:
      definition: consume;transform;produce      # 省略它的前提是「容器里只有一个函数 Bean」
    stream:
      output-bindings: streamSend                # 逻辑名 → 框架补 -out-0，启动即建 binding（见「关键机制」）
      bindings:
        consume-in-0:
          destination: stream-demo-topic
          contentType: text/plain                # 默认 application/json：文本会被序列化成带引号的 JSON
          group: stream-demo-group               # = Kafka 的 group.id
          consumer:
            max-attempts: 3                      # 含第一次调用，共 3 次
            back-off-initial-interval: 1000      # 退避 1s、2s（倍数 2，上限 10s）
        transform-in-0:   { destination: stream-demo-topic, group: stream-demo-transform-group }
        transform-out-0:  { destination: stream-demo-transform-output }
        streamSend-out-0: { destination: stream-demo-topic }
        produce-out-0:    { }                    # 故意不配 destination：现场对照默认命名
      poller: { fixed-delay: 3000 }              # Supplier 的触发节奏
      kafka:
        binder: { brokers: 127.0.0.1:9092, replication-factor: 1 }
        bindings:
          consume-in-0: { consumer: { enable-dlq: true, dlq-name: stream-demo-dlq } }   # 死信队列
```

## 关键机制

**binding 名 → topic**（实测：destination 的默认值是 **binding 名本身**，不是函数名，也不拼 group）：

| binding 名 | 来源 | 不配 destination 时的 topic（实测） | 本模块最终 topic |
| --- | --- | --- | --- |
| `consume-in-0` | 方法名 `consume` + `-in-0` | `consume-in-0` | `stream-demo-topic`（显式） |
| `transform-in-0` / `transform-out-0` | 方法名 `transform` + `-in-0`/`-out-0` | `transform-in-0` / `transform-out-0` | `stream-demo-topic` / `stream-demo-transform-output` |
| `produce-out-0` | 方法名 `produce` + `-out-0` | `produce-out-0` | 同左（故意不配，用来对照） |
| `streamSend-out-0` | `output-bindings: streamSend` + `-out-0` | `streamSend-out-0` | `stream-demo-topic` |

多入参函数用下标区分（`BiFunction` → `-in-0`/`-in-1`）。`definition` 只写 `consume;transform` 时 **`produce-out-0` 绑定直接消失、
日志里再无 `[produce]`**（实测）；容器里仍有 `audit` 这个 Bean，但 `/actuator/bindings` 里没有它（实测）。

**group / 分区 / offset**：`group` 映射为 Kafka `group.id`——同 group 竞争消费（一条消息只被组内一个实例处理），不同 group 各自拿全量。
实测：8230/8231 同组时 12 条消息按 3 : 9 分掉（合计 12，不重复不丢），`kafka-consumer-groups --describe` 显示分区 0、1 归一个实例、
分区 2 归另一个；8232 换 group 后与 8230 **各拿到全部 3 条**。**并行度上限是分区数**（单分区 topic 下同组多实例只有 1 个在干活），
所以本模块用 `KafkaAdmin`+`NewTopic` 把 `stream-demo-topic` 建成 3 分区——消费端自动建 topic 只建 1 个分区，
`producer.partition-count` 只对生产端建 topic 生效（实测）。另：Kafka Binder **不会**把 group 拼进 topic 名，
RabbitMQ Binder 才会（`queue = destination.group`）——这条是原理，本机没有 RabbitMQ 可对照。

**错误处理与重试**：`max-attempts`（含首次）+ `back-off-initial-interval`（倍数默认 2，上限 `back-off-max-interval` 默认 10s）。
实测 1 条毒消息：消费方法被调用 3 次，间隔 1004ms、2006ms，失败消息头上出现 `deliveryAttempt=3`。
⚠️ **`max-attempts=-1`（无限重试）在 3.2.10 会让应用启动失败**：`Max attempts should be greater than zero.`（`@Min(1)` 校验，实测）。
想近似无限只能写大数——代价是毒消息把分区永久堵死：实测 `max-attempts=1000000` 时该组 `CURRENT-OFFSET` 一直是 `-`、
`LOG-END-OFFSET` 从 8 涨到 9，正常消息一条都进不来，DLQ 也永远不被触发。**结论：重试次数不能代替 DLQ。**

**死信队列（DLQ）**：`enable-dlq: true` + `dlq-name`（不配时的默认名文档给的是 `<destination>.DLQ`，本机未单独实测）。
重试耗尽后消息被投递到 DLQ topic，并带上排障头 `x-original-topic`、`x-original-partition`、`x-original-offset`、
`x-exception-message`/`x-exception-stacktrace`（实测读到）。DLQ 的分区数跟源 topic（实测 3 个分区），
且 **DLQ 跨组共享**：另一个消费者组从头重读同一条毒消息时会再次重试、再次投递，DLQ 里就出现同一业务消息的多份副本（实测，见「思考点」）。

**消息转换与消息头**：默认 `contentType=application/json`，文本消息务必改 `text/plain`；payload 类型写 `Message<String>`
就能拿到 Binder 注入的原始头（2.x 注解模型里等价写法是 `@Header(KafkaHeaders.RECEIVED_TOPIC)`）。实测拿到的头：
`kafka_receivedTopic`、`kafka_receivedPartitionId`、`kafka_offset`、`kafka_groupId`、`kafka_receivedMessageKey`——排障时比 payload 有用。

**StreamBridge 的 binding 生命周期**：`spring.cloud.stream.bindings.streamSend-out-0.*` 只描述「怎么连」，不代表 binding 会被创建；
不声明 `output-bindings` 时首次发送可能撞上 `Dispatcher has no subscribers for channel 'unknown.channel.name'`（实测撞到两次；
把它覆盖成空再试又成功了 → 这是**竞态**而非必然失败，但没人的线上第一次请求愿意赌），所以本模块显式声明
（启动日志可见 `Channel 'stream-demo.streamSend-out-0' has 1 subscriber(s).`）。声明要写**逻辑名** `streamSend`，
写成 `streamSend-out-0` 会被再补一次后缀、建出无用的 `streamSend-out-0-out-0`（实测）。

## 动手验证

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)   # Kafka 4.x 命令行工具要求 JDK 17+，否则 UnsupportedClassVersionError
cd /Users/wkedong/Documents/wkdong/workspace/Spring-Cloud
mvn -B -pl stream-demo -am package -DskipTests
nohup java -jar stream-demo/target/stream-demo-0.0.1-SNAPSHOT.jar > /tmp/stream-demo-8230.log 2>&1 &
B=/opt/homebrew/opt/kafka/bin

# 1) 发送 → 消费 → 计数（异步链路的三步证据）
curl -s -X POST -H 'Content-Type: text/plain' -d 'hello-stream-1' http://127.0.0.1:8230/stream/send
curl -s 'http://127.0.0.1:8230/stream/messages?limit=1' | python3 -m json.tool
curl -s  http://127.0.0.1:8230/stream/stats

# 2) 毒消息：重试 → DLQ（本模块最有价值的一步）
curl -s -X POST -H 'Content-Type: text/plain' -d 'please-fail-1' http://127.0.0.1:8230/stream/send
sleep 8 && curl -s http://127.0.0.1:8230/stream/stats     # 看 failedAttempts 与 recentFailureAttempts 的间隔
$B/kafka-console-consumer --bootstrap-server 127.0.0.1:9092 --topic stream-demo-dlq --from-beginning --max-messages 1

# 3) Stream 建了哪些 topic、几个分区
$B/kafka-topics --bootstrap-server 127.0.0.1:9092 --list
$B/kafka-topics --bootstrap-server 127.0.0.1:9092 --describe --topic stream-demo-topic

# 4) 同组负载均衡：起 8231（沿用 stream-demo-group）
nohup java -jar stream-demo/target/stream-demo-0.0.1-SNAPSHOT.jar --server.port=8231 > /tmp/stream-demo-8231.log 2>&1 &
sleep 30 && $B/kafka-consumer-groups --bootstrap-server 127.0.0.1:9092 --describe --group stream-demo-group
curl -s -X POST http://127.0.0.1:8230/stream/reset; curl -s -X POST http://127.0.0.1:8231/stream/reset
for i in $(seq 1 12); do curl -s -o /dev/null -X POST -H 'Content-Type: text/plain' -d "lb-msg-$i" \
      http://127.0.0.1:8230/stream/send; done
sleep 6 && curl -s http://127.0.0.1:8230/stream/stats && curl -s http://127.0.0.1:8231/stream/stats
pkill -f "server.port=8231"

# 5) 组间广播：起 8232，换一个消费者组
nohup java -jar stream-demo/target/stream-demo-0.0.1-SNAPSHOT.jar --server.port=8232 \
      --spring.cloud.stream.bindings.consume-in-0.group=stream-demo-broadcast-group > /tmp/stream-demo-8232.log 2>&1 &

pkill -f "stream-demo-0.0.1-SNAPSHOT.jar"   # 只杀自己起的 jar，Kafka/Nacos/Prometheus 不动
```

**本机实测输出摘要**（2026-10-08，Corretto 17）：

```text
# 发送/消费/计数：{"code":0,...,"binding":"streamSend-out-0","destination":"stream-demo-topic","sent":true,"costMs":4}
  seq=1 payload=hello-stream-1 topic=stream-demo-topic partition=0 offset=0 | receivedTotal=3 | failedAttempts=0
  kafkaHeaders={kafka_receivedTopic, kafka_receivedPartitionId:0, kafka_offset:0, kafka_groupId:stream-demo-group,
                kafka_receivedMessageKey:order-1001（带 ?key= 发送时）}

# 毒消息（max-attempts=3 + back-off-initial-interval=1000）：3 次调用，间隔 1004ms、2006ms（退避 1s、2s，倍数 2）
  failedAttempts = 3 | 日志：... deliveryAttempt=3 ... IllegalStateException: 模拟消费失败（毒消息，命中 fail）：please-fail-1

# DLQ：重试耗尽后能读到那条毒消息
  $ kafka-console-consumer --topic stream-demo-dlq --from-beginning --max-messages 1
  please-fail-1
  Processed a total of 1 messages
  记录头：x-original-topic:stream-demo-topic  x-original-partition:0  x-exception-message:...模拟消费失败...

# topic 与分区
  __consumer_offsets  produce-out-0  sc-teaching-test  stream-demo-dlq  stream-demo-topic  stream-demo-transform-output
  stream-demo-topic PartitionCount: 3 | stream-demo-transform-output PartitionCount: 3（内容形如 TRANSFORMED(HELLO-STREAM-1)）
  produce-out-0：Supplier 定时生产（默认 destination 就是 binding 名），内容 supplier-heartbeat-1 / -2 ...

# 同组负载均衡（12 条，8230/8231 同 group）：分区 0,1 → 8231、分区 2 → 8230；receivedTotal 8230=3、8231=9（合计 12，不重复不丢）
# 不同组广播（3 条）：8230 与 8232 的 receivedTotal 都是 3
# 无限重试：① max-attempts=-1 → 启动失败：rejected value [-1]; [Max attempts should be greater than zero.]
#           ② max-attempts=1000000 → 能启动但分区被堵死：failedAttempts=6，间隔 1003/2003/4006/8002/10005ms，
#              该组 CURRENT-OFFSET="-"、LOG-END-OFFSET 8→9，DLQ 各分区末尾 offset 保持 0:2 1:2 2:0 不变（从未触发）

# 本机 Kafka 里已留有上述实测数据（含 DLQ 里的毒消息）。想从零重跑：先删 topic 再重启应用
#   for t in stream-demo-topic stream-demo-transform-output stream-demo-dlq; do
#     $B/kafka-topics --bootstrap-server 127.0.0.1:9092 --delete --topic $t; done
```

## 思考点

- **幂等消费**：实测中同一条毒消息在 DLQ 里出现两份——8230（`stream-demo-group`）与 8232（新组从 `earliest` 重读）各自重试、各自投递。
  Kafka 只给「至少一次」，重复是常态：消费端要用业务唯一键去重（幂等表 / 唯一索引 / Redis SETNX），而不是指望「不重复」；DLQ 消费端同理。
- **顺序性**：分区内有序、跨分区无序；相同 key 必进同一分区（可用 `?key=` 验证）。而重试会**阻塞**分区
  （上面 10005ms 的间隔就是同一分区在等重试），想既保序又并行，只能按业务键分区 + 尽量缩短重试。
- **事务消息边界**：Stream/Kafka 的事务只在 Kafka 内部生效，「消费 + 写库」不是原子的。生产做法是 outbox 表
  （本地事务写业务表 + outbox，再由后台投递）或消费端幂等表；指望 `@Transactional` 覆盖消息投递是常见误区。
- **Stream 与直接用 spring-kafka 的取舍**：

  | 维度 | Spring Cloud Stream | spring-kafka（`KafkaTemplate`/`@KafkaListener`） |
  | --- | --- | --- |
  | 抽象层级 | 高：binding + 函数，topic/分区/重试由配置驱动 | 低：直接操作 Kafka API，Kafka 特性随手可用 |
  | 可移植性 | 强：换 RabbitMQ 只改依赖与 binder 配置 | 无：代码与 Kafka 绑定 |
  | 编程模型 | 函数式 Bean，易单测（`TestChannelBinder` 直接喂输入、断言输出） | 监听器/模板，测试要真 broker 或 MockConsumer |
  | 精细控制 | 需要 Kafka 专属配置（`spring.cloud.stream.kafka.*`） | 灵活：拦截器、自定义分区器、事务、手动 ack 都在手边 |
  | 排查成本 | 多一层抽象：报错先看 `/actuator/bindings` 与 binder 日志 | 报错直接落在 Kafka 客户端，链路短 |

  选择标准：**业务只需要「事件进出」→ Stream；需要 Kafka 特有能力 → 直接用 spring-kafka**。两者也能混用（本模块用 `KafkaAdmin` 声明分区数）。
