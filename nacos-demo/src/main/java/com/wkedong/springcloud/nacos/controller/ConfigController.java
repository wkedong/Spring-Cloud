package com.wkedong.springcloud.nacos.controller;

import com.wkedong.springcloud.nacos.service.ConfigQueryService;
import com.wkedong.springcloud.nacos.web.ApiResponse;
import com.wkedong.springcloud.nacos.web.dto.ConfigPreviewView;
import com.wkedong.springcloud.nacos.web.dto.PriorityReportView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 配置中心相关的端点：看得见「配置从哪来」，才谈得上排查。
 *
 * @author wkedong
 */
@RestController
@RequestMapping("/nacos/config")
public class ConfigController {

    private final ConfigQueryService configQueryService;

    public ConfigController(ConfigQueryService configQueryService) {
        this.configQueryService = configQueryService;
    }

    /**
     * 配置预览：实际拉到的 dataId、四个探针 bean 的取值、刷新事件次数、进程运行时长。
     * <p>
     * 动态刷新的验证方式：先记下这里的输出，改完 Nacos 配置再请求一次——
     * 值变了但 {@code uptimeSeconds} 在增长，就说明是热更新而不是重启。
     */
    @GetMapping("/preview")
    public ApiResponse<ConfigPreviewView> preview() {
        return ApiResponse.ok(configQueryService.preview());
    }

    /**
     * 配置优先级报告：属性源顺序 + 每个样本 key 的生效来源与全部候选值。
     * <p>
     * 命令行覆盖的实验：先用默认参数请求一次，再用
     * {@code --nacos.demo.priority=from-command-line} 启动，对比同一 key 的输出。
     */
    @GetMapping("/priority")
    public ApiResponse<PriorityReportView> priority() {
        return ApiResponse.ok(configQueryService.priority());
    }
}
