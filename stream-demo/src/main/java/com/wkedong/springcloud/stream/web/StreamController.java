package com.wkedong.springcloud.stream.web;

import com.wkedong.springcloud.stream.messaging.ConsumedMessage;
import com.wkedong.springcloud.stream.messaging.MessageBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息驱动模块的 HTTP 入口：触发生产 + 回读消费结果。
 * <p>
 * 为什么要有 HTTP 触发生产：生产者不一定有「业务事件」可用（比如运营要手动补发一条消息、
 * 上游系统只会调 REST）。这种情况下 {@link StreamBridge} 是标准做法——
 * 它让你在**任意位置**按 binding 名发送消息，而不用先把消息包成一个 Supplier/Function 的返回值，
 * 也不用注入底层 KafkaTemplate，业务代码依然与中间件无关。
 * <p>
 * 为什么要有回读端点：消息是异步的，「发了」不等于「消费到了」。把消费到的消息写进内存缓冲后
 * 用 {@code /stream/messages}、{@code /stream/stats} 回读，才能把异步链路变成可验证的证据
 * （这也是生产环境「消息可见性」的最小形态：消费计数 + 最后一条 + 消费线程名）。
 *
 * @author wkedong
 */
@RestController
@RequestMapping("/stream")
public class StreamController {

    private static final Logger log = LoggerFactory.getLogger(StreamController.class);

    /**
     * 发送用的 binding 名。注意它不是「topic 名」：binding 名是业务侧的逻辑名，
     * 真正的 topic 由 {@code spring.cloud.stream.bindings.streamSend-out-0.destination} 决定。
     */
    private static final String SEND_BINDING = "streamSend-out-0";

    private final StreamBridge streamBridge;
    private final MessageBuffer buffer;

    /** 消费用的消费者组：同一 group 的多个实例是「竞争消费」，不同 group 之间是「广播」 */
    @Value("${spring.cloud.stream.bindings.consume-in-0.group:anonymous}")
    private String consumerGroup;

    /** 消费端 topic（consume-in-0 的 destination） */
    @Value("${spring.cloud.stream.bindings.consume-in-0.destination:未配置}")
    private String consumingTopic;

    /** 发送目的地（topic）：必须与 consume-in-0 的 destination 一致，否则消息进不了消费链路 */
    @Value("${spring.cloud.stream.bindings.streamSend-out-0.destination:未配置}")
    private String sendDestination;

    /** 转发结果 topic（transform-out-0 的 destination） */
    @Value("${spring.cloud.stream.bindings.transform-out-0.destination:未配置}")
    private String transformOutputTopic;

    /** 死信队列 topic 名 */
    @Value("${spring.cloud.stream.kafka.bindings.consume-in-0.consumer.dlq-name:未配置}")
    private String dlqTopic;

    /** 当前实例端口：多实例实验时用来区分「消息被哪个实例消费了」 */
    @Value("${server.port}")
    private String serverPort;

    public StreamController(StreamBridge streamBridge, MessageBuffer buffer) {
        this.streamBridge = streamBridge;
        this.buffer = buffer;
    }

    /**
     * 发送一条消息（生产环境最常用的「按需发送」）。
     * <p>
     * 注意返回值语义：code=0 只表示**已交给 Binder 发送成功**，不代表下游已消费；
     * 想确认消费结果要再查 {@code /stream/messages}。
     * <p>
     * 可选参数 key 是 Kafka 的消息键：**相同 key 一定落进同一分区**，
     * 而同一分区内是有序的——这是「分区内有序、跨分区无序」这条规则的操作入口。
     *
     * @param content 消息内容（纯文本请求体，例如 curl -d 'hello'）
     * @param key     可选消息键；不传则由 Kafka 默认分区器决定分区
     */
    @PostMapping("/send")
    public ApiResponse<Map<String, Object>> send(@RequestBody(required = false) String content,
                                                 @RequestParam(required = false) String key) {
        if (content == null || content.trim().isEmpty()) {
            return ApiResponse.fail(400, "消息内容不能为空：请用 curl -X POST -d '内容' 发送");
        }
        long start = System.currentTimeMillis();
        boolean sent;
        try {
            if (key == null || key.isEmpty()) {
                sent = streamBridge.send(SEND_BINDING, content);
            } else {
                // 键必须用 byte[]：Binder 会把它写成 Kafka 记录的 key（消费端可从 kafka_receivedMessageKey 读到）
                Message<String> message = MessageBuilder.withPayload(content)
                        .setHeader(KafkaHeaders.MESSAGE_KEY, key.getBytes(StandardCharsets.UTF_8))
                        .build();
                sent = streamBridge.send(SEND_BINDING, message);
            }
        } catch (Exception e) {
            log.error("发送失败：binding={} content={}", SEND_BINDING, content, e);
            return ApiResponse.fail(500, "发送异常：" + e.getMessage());
        }

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("binding", SEND_BINDING);
        data.put("destination", sendDestination);
        data.put("payload", content);
        data.put("key", key);
        data.put("payloadBytes", content.getBytes(StandardCharsets.UTF_8).length);
        data.put("sent", sent);
        data.put("costMs", System.currentTimeMillis() - start);
        data.put("hint", "已交给 Binder；消费结果请看 GET /stream/messages");
        log.info("[send] binding={} destination={} key={} sent={} payload={}",
                SEND_BINDING, sendDestination, key, sent, content);
        return sent ? ApiResponse.ok(data) : ApiResponse.fail(500, "StreamBridge.send 返回 false（binding 未就绪？）");
    }

    /** 最近 N 条消费记录（默认 10 条，最新在后）。 */
    @GetMapping("/messages")
    public ApiResponse<List<ConsumedMessage>> messages(@RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(buffer.recent(limit));
    }

    /** 消费统计：计数、最后一条、消费线程名——「消费者还活着吗、消费到哪了」的第一手证据。 */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("instancePort", serverPort);
        data.put("consumerGroup", consumerGroup);
        data.put("consumingTopic", consumingTopic);
        data.put("sendDestination", sendDestination);
        data.put("transformOutputTopic", transformOutputTopic);
        data.put("dlqTopic", dlqTopic);
        data.put("receivedTotal", buffer.getReceivedTotal());
        data.put("failedAttempts", buffer.getFailedAttempts());
        data.put("recentFailureAttempts", failureTimeline());
        data.put("bufferSize", buffer.size());
        data.put("bufferCapacity", buffer.getCapacity());
        data.put("consumerThreads", buffer.getConsumerThreads());
        data.put("lastMessage", buffer.getLastMessage());
        data.put("lastFailure", buffer.getLastFailure());
        data.put("lastFailureAt", buffer.getLastFailureAt());
        data.put("poisonRule", "payload 含 'fail'（忽略大小写）抛异常，用于演示重试与 DLQ");
        return ApiResponse.ok(data);
    }

    /**
     * 清零本实例的计数与缓冲。
     * <p>
     * 多实例实验必备：不清零的话，消费总数是「历史累计」，无法判断刚发的一批消息被谁消费了。
     * 注意它只清当前实例的内存计数，Kafka 里的消息与 offset 不受影响。
     */
    @PostMapping("/reset")
    public ApiResponse<String> reset() {
        buffer.clear();
        log.info("[reset] 已清空本实例消费缓冲与计数，port={}", serverPort);
        return ApiResponse.ok("已清空本实例（端口 " + serverPort + "）的消费缓冲与计数");
    }

    /**
     * 把失败尝试整理成「时间线」：相邻两条的间隔就是重试退避的真实间隔，
     * 用来验证 back-off-initial-interval 是否生效（比只看重试次数更有说服力）。
     */
    private List<Map<String, Object>> failureTimeline() {
        List<Map<String, Object>> timeline = new ArrayList<Map<String, Object>>();
        Long previous = null;
        for (MessageBuffer.FailureAttempt attempt : buffer.recentFailures()) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("attemptAt", attempt.getAt());
            item.put("gapFromPreviousMs", previous == null ? null : attempt.getAt() - previous);
            item.put("payload", attempt.getPayload());
            item.put("thread", attempt.getThread());
            timeline.add(item);
            previous = attempt.getAt();
        }
        return timeline;
    }
}
