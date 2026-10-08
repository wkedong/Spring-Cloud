package com.wkedong.springboot.basics.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 定时任务演示。
 * <p>
 * 教学点：
 * <ul>
 *   <li>{@code fixedRate} 按「固定频率」触发（上一次开始后计时），
 *       {@code fixedDelay} 按「上一次结束后计时」，任务耗时超过间隔时 fixedRate 会排队/并发；</li>
 *   <li>cron 用 Spring 的 6 段格式（秒 分 时 日 月 周），与 Linux crontab 的 5 段不同；</li>
 *   <li>默认调度线程池只有 <b>1</b> 个线程：多个 @Scheduled 任务会互相阻塞，
 *       生产应配置 {@code spring.task.scheduling.pool.size}（见 application.yml）。</li>
 * </ul>
 * 观察方式：{@code GET /actuator/basics} 里的定时任务计数与最近触发时间。
 *
 * @author wkedong
 */
@Component
public class ScheduledTasks {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTasks.class);

    private final AtomicInteger fixedRateRuns = new AtomicInteger();
    private final AtomicInteger cronRuns = new AtomicInteger();
    private volatile String lastFixedRateTime = "-";
    private volatile String lastCronTime = "-";

    /** 每 10 秒执行一次（固定频率） */
    @Scheduled(fixedRate = 10_000, initialDelay = 5_000)
    public void reportFixedRate() {
        fixedRateRuns.incrementAndGet();
        lastFixedRateTime = LocalDateTime.now().withNano(0).toString();
        log.info("[定时任务 fixedRate] 第 {} 次执行", fixedRateRuns.get());
    }

    /** 每 30 秒执行一次（cron 表达式，6 段：秒 分 时 日 月 周） */
    @Scheduled(cron = "0/30 * * * * ?")
    public void reportCron() {
        cronRuns.incrementAndGet();
        lastCronTime = LocalDateTime.now().withNano(0).toString();
        log.info("[定时任务 cron] 第 {} 次执行（0/30 * * * * ? = 每 30 秒）", cronRuns.get());
    }

    public int getFixedRateRuns() {
        return fixedRateRuns.get();
    }

    public int getCronRuns() {
        return cronRuns.get();
    }

    public String getLastFixedRateTime() {
        return lastFixedRateTime;
    }

    public String getLastCronTime() {
        return lastCronTime;
    }
}
