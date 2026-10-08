package com.wkedong.boot.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * starter 自动装配出来的能力类：记录操作审计日志。
 * <p>
 * 使用方**不需要**自己 new、也不需要写 @Bean——只要引入本 starter 并（可选）配置 basics.audit.*，
 * 就能直接 {@code @Autowired AuditLogger}。这正是 Spring Boot starter 的价值：
 * 「依赖即能力」，装配细节封装在 AutoConfiguration 里。
 *
 * @author wkedong
 */
public class AuditLogger {

    private static final Logger log = LoggerFactory.getLogger(AuditLogger.class);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final AuditProperties properties;
    private final Deque<Record> buffer = new ArrayDeque<>();
    private final AtomicLong total = new AtomicLong();

    public AuditLogger(AuditProperties properties) {
        this.properties = properties;
    }

    /** 记录一次操作 */
    public void record(String action, String detail) {
        record(action, detail, 0L);
    }

    /** 记录一次操作（带耗时，超阈值打 WARN） */
    public void record(String action, String detail, long costMillis) {
        String safeDetail = truncate(detail);
        total.incrementAndGet();
        if (costMillis >= properties.getSlowThreshold().toMillis()) {
            log.warn("{} {} -> {} (slow: {}ms)", properties.getPrefix(), action, safeDetail, costMillis);
        } else if ("DEBUG".equalsIgnoreCase(properties.getLevel())) {
            log.debug("{} {} -> {}", properties.getPrefix(), action, safeDetail);
        } else {
            log.info("{} {} -> {}", properties.getPrefix(), action, safeDetail);
        }
        synchronized (buffer) {
            if (buffer.size() >= properties.getBufferSize()) {
                buffer.pollFirst();
            }
            buffer.addLast(new Record(LocalDateTime.now(), action, safeDetail, costMillis));
        }
    }

    /** 最近记录（新→旧），供 /actuator/basics 或演示接口查询 */
    public List<Record> recentRecords() {
        synchronized (buffer) {
            List<Record> copy = new ArrayList<>(buffer);
            Collections.reverse(copy);
            return copy;
        }
    }

    /** 累计记录条数 */
    public long totalRecords() {
        return total.get();
    }

    /** 当前生效的配置（演示「配置真的被 starter 读到了」） */
    public AuditProperties currentProperties() {
        return properties;
    }

    private String truncate(String detail) {
        if (detail == null) {
            return "";
        }
        int max = properties.getMaxDetailLength();
        return detail.length() <= max ? detail : detail.substring(0, max) + "...";
    }

    /** 一条审计记录 */
    public static class Record {
        private final LocalDateTime time;
        private final String action;
        private final String detail;
        private final long costMillis;

        Record(LocalDateTime time, String action, String detail, long costMillis) {
            this.time = time;
            this.action = action;
            this.detail = detail;
            this.costMillis = costMillis;
        }

        public String getTime() {
            return TIME.format(time);
        }

        public Instant getInstant() {
            return time.atZone(java.time.ZoneId.systemDefault()).toInstant();
        }

        public String getAction() {
            return action;
        }

        public String getDetail() {
            return detail;
        }

        public long getCostMillis() {
            return costMillis;
        }
    }

    /** 便于文档里说明 Duration 单位：慢操作阈值 */
    public Duration slowThreshold() {
        return properties.getSlowThreshold();
    }
}
