package com.wkedong.springcloud.stream.messaging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 消费结果的内存「环形缓冲」+ 计数器：让异步的消费行为变成可 curl 观察的同步证据。
 * <p>
 * 设计要点（为什么这么写）：
 * <ul>
 *   <li>写方是 Binder 的消费线程、读方是 HTTP 线程 → 用 {@link ConcurrentLinkedQueue} 免锁并发；</li>
 *   <li>无界队列会随运行时间无限增长（教学 demo 长期挂着会 OOM）→ 超过容量就从队首丢弃，
 *       这就是「环形缓冲」语义：只保留最近 N 条；</li>
 *   <li>计数用 {@link AtomicLong}：{@code receivedTotal} 是「成功消费总数」，
 *       {@code failedAttempts} 是「消费方法抛异常的次数」——重试会让同一条消息被反复调用，
 *       所以 failedAttempts 恰好等于「重试次数 × 消息数」，是观察 maxAttempts 的直接证据；</li>
 *   <li>只存内存、不落库：进程重启即清零，这本身也是「Kafka 才是消息真身、应用只是消费者」的体现。</li>
 * </ul>
 *
 * @author wkedong
 */
@Component
public class MessageBuffer {

    /** 最多保留多少条消费记录（可在 application.yml 里调整） */
    private final int capacity;

    private final Queue<ConsumedMessage> buffer = new ConcurrentLinkedQueue<ConsumedMessage>();
    /** 最近的失败尝试（含时间戳）：用相邻两次的间隔反推 back-off 退避是否按配置生效 */
    private final Queue<FailureAttempt> failures = new ConcurrentLinkedQueue<FailureAttempt>();
    private final AtomicLong receivedTotal = new AtomicLong(0);
    private final AtomicLong failedAttempts = new AtomicLong(0);
    private final AtomicLong sequence = new AtomicLong(0);
    /** 消费线程名集合：观察 Binder 到底用几个线程消费 */
    private final Set<String> consumerThreads = new LinkedHashSet<String>();

    private volatile ConsumedMessage lastMessage;
    private volatile String lastFailure;
    private volatile long lastFailureAt;

    public MessageBuffer(@Value("${stream-demo.buffer-capacity:50}") int capacity) {
        this.capacity = capacity;
    }

    /**
     * 记录一条成功消费的消息。
     *
     * @return 落库后的记录（含自增序号）
     */
    public ConsumedMessage record(String payload, String topic, Integer partition, Long offset,
                                  java.util.Map<String, String> kafkaHeaders) {
        ConsumedMessage message = new ConsumedMessage();
        message.setSeq(sequence.incrementAndGet());
        message.setPayload(payload);
        message.setReceivedAt(Instant.now().toString());
        message.setTopic(topic);
        message.setPartition(partition);
        message.setOffset(offset);
        message.setThread(Thread.currentThread().getName());
        if (kafkaHeaders != null) {
            message.setKafkaHeaders(kafkaHeaders);
        }

        synchronized (consumerThreads) {
            consumerThreads.add(message.getThread());
        }
        buffer.add(message);
        // 超容量丢最旧的：环形缓冲
        while (buffer.size() > capacity) {
            buffer.poll();
        }
        lastMessage = message;
        receivedTotal.incrementAndGet();
        return message;
    }

    /** 记录一次消费失败（重试时会反复调用，因此这里天然记录了「尝试次数」）。 */
    public void recordFailure(String payload, String errorMessage) {
        long now = System.currentTimeMillis();
        failedAttempts.incrementAndGet();
        lastFailure = "payload=" + payload + " -> " + errorMessage;
        lastFailureAt = now;
        failures.add(new FailureAttempt(payload, errorMessage, now, Thread.currentThread().getName()));
        // 只保留最近 20 次尝试，避免毒消息无限重试时把内存撑爆
        while (failures.size() > 20) {
            failures.poll();
        }
    }

    /** 最近若干次失败尝试：相邻两条的间隔就是重试的退避间隔。 */
    public List<FailureAttempt> recentFailures() {
        return new ArrayList<FailureAttempt>(failures);
    }

    /** 最近 limit 条（最新在后，便于阅读「顺序」）。 */
    public List<ConsumedMessage> recent(int limit) {
        List<ConsumedMessage> all = new ArrayList<ConsumedMessage>(buffer);
        if (limit <= 0 || limit >= all.size()) {
            return all;
        }
        return new ArrayList<ConsumedMessage>(all.subList(all.size() - limit, all.size()));
    }

    public long getReceivedTotal() {
        return receivedTotal.get();
    }

    public long getFailedAttempts() {
        return failedAttempts.get();
    }

    public int getCapacity() {
        return capacity;
    }

    public int size() {
        return buffer.size();
    }

    public ConsumedMessage getLastMessage() {
        return lastMessage;
    }

    public String getLastFailure() {
        return lastFailure;
    }

    public long getLastFailureAt() {
        return lastFailureAt;
    }

    /** 各实例的计数器互相独立：多实例负载均衡时正好用各自计数之和来验证「一条只被消费一次」。 */
    public List<String> getConsumerThreads() {
        synchronized (consumerThreads) {
            return new ArrayList<String>(consumerThreads);
        }
    }

    /** 供分组/广播实验前清零计数。 */
    public void clear() {
        buffer.clear();
        failures.clear();
        receivedTotal.set(0);
        failedAttempts.set(0);
        sequence.set(0);
        synchronized (consumerThreads) {
            consumerThreads.clear();
        }
        lastMessage = null;
        lastFailure = null;
        lastFailureAt = 0;
    }

    /**
     * 一次失败尝试的记录。
     * <p>
     * 为什么要记时间戳：{@code max-attempts} 只说明「试几次」，
     * 而退避策略（back-off）只有看相邻两次尝试的间隔才能验证是否真的生效。
     */
    public static class FailureAttempt {

        private String payload;
        private String error;
        /** 本次尝试的时间（epoch 毫秒） */
        private long at;
        private String thread;

        public FailureAttempt(String payload, String error, long at, String thread) {
            this.payload = payload;
            this.error = error;
            this.at = at;
            this.thread = thread;
        }

        public String getPayload() {
            return payload;
        }

        public String getError() {
            return error;
        }

        public long getAt() {
            return at;
        }

        public String getThread() {
            return thread;
        }
    }
}
