package com.wkedong.springcloud.stream.messaging;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一条「被消费到」的消息记录。
 * <p>
 * 为什么要做成对象而不是只存 payload：教学与排障真正需要的是**证据链**——
 * 这条消息来自哪个 topic、哪个分区、offset 是多少、由哪个消费线程处理。
 * 排查「消息丢了 / 重复消费 / 分区不均衡」时，这几个字段比 payload 本身更关键。
 *
 * @author wkedong
 */
public class ConsumedMessage {

    /** 本实例内的自增序号，用来确认「顺序」与「是否有重复」 */
    private long seq;
    private String payload;
    private String receivedAt;
    /** 来自 Kafka 原始头 kafka_receivedTopic（由 Binder 注入） */
    private String topic;
    /** 来自 Kafka 原始头 kafka_receivedPartitionId */
    private Integer partition;
    /** 来自 Kafka 原始头 kafka_offset */
    private Long offset;
    /** 处理这条消息的消费线程名，用于观察 Binder 的线程模型 */
    private String thread;
    /** 所有 kafka_ 开头的原始头（Binder 注入的元数据），原样展示便于对照 */
    private Map<String, String> kafkaHeaders = new LinkedHashMap<String, String>();

    public long getSeq() {
        return seq;
    }

    public void setSeq(long seq) {
        this.seq = seq;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(String receivedAt) {
        this.receivedAt = receivedAt;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public Integer getPartition() {
        return partition;
    }

    public void setPartition(Integer partition) {
        this.partition = partition;
    }

    public Long getOffset() {
        return offset;
    }

    public void setOffset(Long offset) {
        this.offset = offset;
    }

    public String getThread() {
        return thread;
    }

    public void setThread(String thread) {
        this.thread = thread;
    }

    public Map<String, String> getKafkaHeaders() {
        return kafkaHeaders;
    }

    public void setKafkaHeaders(Map<String, String> kafkaHeaders) {
        this.kafkaHeaders = kafkaHeaders;
    }
}
