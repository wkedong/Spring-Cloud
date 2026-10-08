package com.wkedong.springcloud.stream.config;

import com.wkedong.springcloud.stream.messaging.MessageBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 函数式编程模型三件套（Spring Cloud Stream 3.x 起推荐，取代 2.x 的
 * {@code @EnableBinding} / {@code @StreamListener}）。
 * <p>
 * 核心规则：**方法（Bean）名就是逻辑名**，框架按名字生成 binding：
 * <pre>
 * Consumer&lt;T&gt;           → &lt;beanName&gt;-in-0            （只进不出）
 * Function&lt;T, R&gt;       → &lt;beanName&gt;-in-0 / -out-0   （进出各一个）
 * Supplier&lt;T&gt;          → &lt;beanName&gt;-out-0           （只出不进，由 poller 定时触发）
 * </pre>
 * 多个入参用 {@code -0/-1} 下标区分（例如 {@code BiFunction} 是 {@code -in-0} / {@code -in-1}）。
 * 哪个 topic、哪个消费者组由 {@code spring.cloud.stream.bindings.<binding 名>.*} 配置决定，
 * 业务代码里**不出现任何中间件 API**——这正是 Binder 抽象的价值：换 RabbitMQ 只改依赖和配置。
 * <p>
 * 哪些函数真正生效由 {@code spring.cloud.function.definition} 决定（多个函数 Bean 时必须显式声明，
 * 否则启动期会因为「候选函数不唯一」而报错）；不在 definition 里的函数 Bean 依然存在于
 * ApplicationContext，但「不参与绑定」——本类里的 {@link #audit()} 就是故意留的反例。
 *
 * @author wkedong
 */
@Configuration
public class StreamFunctionConfig {

    private static final Logger log = LoggerFactory.getLogger(StreamFunctionConfig.class);

    /**
     * 毒消息判定规则：payload 里含 "fail"（忽略大小写）就抛异常。
     * <p>
     * 为什么要故意造毒消息：重试（maxAttempts / back-off）和死信队列（DLQ）只有真的抛异常
     * 才能观察到行为，否则二者都是纸面配置。
     */
    private static final String POISON_KEYWORD = "fail";

    /**
     * 消费：{@code Consumer<Message<String>>}，binding 名 {@code consume-in-0}。
     * <p>
     * 为什么泛型用 {@code Message<String>} 而不是 {@code String}：
     * 直接把 payload 类型写成 String 也能收消息，但拿不到 Kafka 原始头。用 Message 包装后可以读到
     * Binder 注入的 {@code kafka_receivedTopic} / {@code kafka_receivedPartitionId} / {@code kafka_offset}
     * 等元数据（2.x 注解模型里等价写法是方法参数上的
     * {@code @Header(KafkaHeaders.RECEIVED_TOPIC) String topic}）。
     * 排障时这些头比 payload 更重要：能直接定位「消息来自哪个分区、偏移量多少、是否重复」。
     *
     * @param buffer 消费结果的内存缓冲，供 HTTP 端点回读
     */
    @Bean
    public Consumer<Message<String>> consume(MessageBuffer buffer) {
        return message -> {
            String payload = message.getPayload();
            Map<String, String> kafkaHeaders = extractKafkaHeaders(message);

            if (isPoison(payload)) {
                // 抛异常 → 触发 Stream 的重试策略；重试次数用尽后由 Binder 投递到 DLQ
                buffer.recordFailure(payload, "payload 命中毒消息关键字 '" + POISON_KEYWORD + "'");
                throw new IllegalStateException("模拟消费失败（毒消息，命中 " + POISON_KEYWORD + "）：" + payload);
            }

            buffer.record(payload, kafkaHeaders.get("kafka_receivedTopic"),
                    toInteger(kafkaHeaders.get("kafka_receivedPartitionId")),
                    toLong(kafkaHeaders.get("kafka_offset")), kafkaHeaders);
            log.info("[consume] topic={} partition={} offset={} thread={} payload={}",
                    kafkaHeaders.get("kafka_receivedTopic"), kafkaHeaders.get("kafka_receivedPartitionId"),
                    kafkaHeaders.get("kafka_offset"), Thread.currentThread().getName(), payload);
        };
    }

    /**
     * 处理并转发：{@code Function<String, String>}，binding 名 {@code transform-in-0} / {@code transform-out-0}。
     * <p>
     * Function 天然是「消费 → 计算 → 生产」的一条流水线，中间的 topic 由 out 绑定决定；
     * 输入绑定在配置里指向与 consume 相同的 topic，但用**不同的消费者组**，
     * 于是一条消息会同时被 consume（入缓冲）和 transform（加工转发）拿到——这就是「组内负载均衡、
     * 组间广播」的直接应用。
     */
    @Bean
    public Function<String, String> transform() {
        return payload -> {
            String result = "TRANSFORMED(" + (payload == null ? "" : payload.toUpperCase()) + ")";
            log.info("[transform] {} -> {}", payload, result);
            return result;
        };
    }

    /**
     * 定时生产：{@code Supplier<String>}，binding 名 {@code produce-out-0}。
     * <p>
     * Supplier 自己没有业务触发点，由 Stream 的 poller 按
     * {@code spring.cloud.stream.poller.fixed-delay}（默认 1s）周期性调用，
     * 适合「定时拉取外部数据后投递」的场景（把定时任务与投递解耦）。
     * 返回值就是消息体；返回 null 表示本次不发送。
     */
    @Bean
    public Supplier<String> produce() {
        final AtomicLong counter = new AtomicLong(0);
        return () -> {
            String heartbeat = "supplier-heartbeat-" + counter.incrementAndGet();
            log.info("[produce] 定时生产：{}", heartbeat);
            return heartbeat;
        };
    }

    /**
     * 故意不写进 {@code spring.cloud.function.definition} 的函数：用于证明
     * 「definition 之外函数 Bean 存在但不会被绑定」（启动后 /actuator/bindings 里找不到 audit-*，
     * kafka-topics 里也不会有它的 topic）。
     */
    @Bean
    public Function<String, String> audit() {
        return payload -> "[audit]" + payload;
    }

    /** 只取 Binder 注入的 kafka_ 前缀原始头，避免把不相关的框架头刷到接口里。 */
    private static Map<String, String> extractKafkaHeaders(Message<?> message) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        for (Map.Entry<String, Object> entry : message.getHeaders().entrySet()) {
            if (entry.getKey() != null && entry.getKey().startsWith("kafka_")) {
                result.put(entry.getKey(), render(entry.getValue()));
            }
        }
        return result;
    }

    /**
     * 头值统一转成可读字符串：消息键（kafka_receivedMessageKey）是 byte[]，
     * 直接用 toString() 会打印成 "[B@1a2b3c"，这里按 UTF-8 还原成原文。
     */
    private static String render(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[]) {
            return new String((byte[]) value, StandardCharsets.UTF_8);
        }
        return String.valueOf(value);
    }

    private static boolean isPoison(String payload) {
        return payload != null && payload.toLowerCase().contains(POISON_KEYWORD);
    }

    private static Integer toInteger(String value) {
        try {
            return value == null ? null : Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long toLong(String value) {
        try {
            return value == null ? null : Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
