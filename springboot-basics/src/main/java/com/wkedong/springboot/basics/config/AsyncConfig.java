package com.wkedong.springboot.basics.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步与线程池配置。
 * <p>
 * 教学点：
 * <ul>
 *   <li><b>不要用默认线程池</b>：@Async 默认用 SimpleAsyncTaskExecutor（每次新建线程），
 *       生产必须自定义，否则高并发下线程爆炸。</li>
 *   <li><b>给线程起名</b>：{@code basics-async-1} 这种前缀能在日志/线程 dump 里一眼定位来源。</li>
 *   <li><b>队列与拒绝策略</b>：队列满了之后的行为要显式选择
 *       （CallerRunsPolicy = 让调用线程自己跑，起到天然背压）。</li>
 *   <li><b>异常处理</b>：返回 void 的 @Async 方法抛异常会「静默丢失」，
 *       必须注册 AsyncUncaughtExceptionHandler。</li>
 * </ul>
 *
 * @author wkedong
 */
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    public static final String EXECUTOR_NAME = "basicsExecutor";

    @Bean(EXECUTOR_NAME)
    public ThreadPoolTaskExecutor basicsExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("basics-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return basicsExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new SimpleAsyncUncaughtExceptionHandler() {
            @Override
            public void handleUncaughtException(Throwable ex, Method method, Object... params) {
                log.error("异步任务执行失败：method={}, 参数个数={}", method.getName(), params.length, ex);
            }
        };
    }
}
