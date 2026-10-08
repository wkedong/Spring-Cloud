package com.wkedong.springcloud.nacos.controller;

import com.wkedong.springcloud.nacos.service.PitfallCheckService;
import com.wkedong.springcloud.nacos.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 踩坑对照端点：把文档里的每一条坑，换成「运行时能否自证」的判断。
 * <p>
 * 用法示例：想验证「不加 spring-cloud-starter-bootstrap 会怎样」，
 * 就用 {@code java -jar ... --spring.cloud.bootstrap.enabled=false} 启动，
 * 再来打这个端点——{@code bootstrapYmlLoaded} 会变成 false，
 * 而启动过程本身会抛出 config import 检查的异常。
 *
 * @author wkedong
 */
@RestController
@RequestMapping("/nacos/pitfall")
public class PitfallController {

    private final PitfallCheckService pitfallCheckService;

    public PitfallController(PitfallCheckService pitfallCheckService) {
        this.pitfallCheckService = pitfallCheckService;
    }

    /**
     * 逐项自检：bootstrap、config-import 检查、命名空间/分组/dataId、Eureka 字段对照。
     */
    @GetMapping("/checklist")
    public ApiResponse<Map<String, Object>> checklist() {
        return ApiResponse.ok(pitfallCheckService.checklist());
    }
}
