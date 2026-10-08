package com.wkedong.springboot.basics.web;

import com.wkedong.springboot.basics.service.AsyncTaskService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 异步演示接口。
 * <pre>
 * GET /api/async/compare?tasks=5&sleepMillis=300   串行 vs 并行 的耗时对比（最能说明问题）
 * GET /api/async/future?index=1                    返回 CompletableFuture，由 MVC 异步处理
 * GET /api/async/void-error?fail=true              触发 void 异步方法的异常处理器
 * </pre>
 * 用 tasks=5&sleepMillis=300 时：串行约 1500ms，并行约 300ms（线程池 4 核并发）。
 *
 * @author wkedong
 */
@RestController
@RequestMapping("/api/async")
public class AsyncController {

    private final AsyncTaskService asyncTaskService;

    public AsyncController(AsyncTaskService asyncTaskService) {
        this.asyncTaskService = asyncTaskService;
    }

    @GetMapping("/compare")
    public ApiResponse<Map<String, Object>> compare(@RequestParam(value = "tasks", defaultValue = "5") int tasks,
                                                    @RequestParam(value = "sleepMillis", defaultValue = "300") long sleepMillis) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tasks", tasks);
        data.put("sleepMillisPerTask", sleepMillis);

        // ① 串行：一个接一个执行
        asyncTaskService.runSync(tasks, sleepMillis, data);

        // ② 并行：提交到自定义线程池，等全部完成
        long start = System.currentTimeMillis();
        List<CompletableFuture<String>> futures = new ArrayList<>();
        for (int i = 1; i <= tasks; i++) {
            futures.add(asyncTaskService.work(i, sleepMillis));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        data.put("asyncCostMillis", System.currentTimeMillis() - start);

        List<String> results = new ArrayList<>();
        for (CompletableFuture<String> future : futures) {
            results.add(future.getNow("(未完成)"));
        }
        data.put("taskResults", results);
        data.put("executedTotal", asyncTaskService.executedCount());
        data.put("hint", "注意每个任务的线程名都是 basics-async-*，说明用的是我们自己的线程池");
        return ApiResponse.ok(data);
    }

    /** 直接返回 CompletableFuture：Spring MVC 会异步处理并在完成后写回响应 */
    @GetMapping("/future")
    public CompletableFuture<ApiResponse<String>> future(@RequestParam(value = "index", defaultValue = "1") int index) {
        return asyncTaskService.work(index, 200).thenApply(ApiResponse::ok);
    }

    /** void 类型异步方法的异常：调用方拿不到，只能靠 AsyncUncaughtExceptionHandler 记录 */
    @GetMapping("/void-error")
    public ApiResponse<String> voidError(@RequestParam(value = "fail", defaultValue = "true") boolean fail) {
        asyncTaskService.fireAndForget(fail);
        return ApiResponse.ok("任务已提交，异常请看应用日志（处理线程：basics-async-*）");
    }
}
