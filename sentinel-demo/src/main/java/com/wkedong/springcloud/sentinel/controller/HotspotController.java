package com.wkedong.springcloud.sentinel.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.wkedong.springcloud.sentinel.service.HotspotService;
import com.wkedong.springcloud.sentinel.web.ApiResponse;
import com.wkedong.springcloud.sentinel.web.DemoLimits;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * 热点参数限流入口。
 * <p>
 * 验证方式（同一秒内连打 10 次，阈值 2 QPS）：
 * <pre>
 * for i in $(seq 1 10); do curl -s "http://127.0.0.1:8220/hotspot?type=iphone"; echo; done   # 前 2 个通过
 * for i in $(seq 1 10); do curl -s "http://127.0.0.1:8220/hotspot?type=macbook"; echo; done  # 独立计数，照样前 2 个通过
 * </pre>
 * 两次结果形状一样，正说明配额是「按参数值」分的，而不是按资源分的。
 * <p>
 * 热点参数规则只能配在 {@code @SentinelResource} 资源上：URL 资源拿不到方法参数值。
 * 这是它与普通流控规则最大的使用差异。
 *
 * @author wkedong
 */
@RestController
@RequestMapping("/hotspot")
@Validated
public class HotspotController {

    private final HotspotService hotspotService;

    public HotspotController(HotspotService hotspotService) {
        this.hotspotService = hotspotService;
    }

    /**
     * 参数 idx=0 即 type；只对热点值限流，其它值各有一份独立配额。
     * <p>
     * 热点参数的值会进入 Sentinel 的统计维度（每个不同值一份配额），
     * 所以必须限制长度——否则调用方可以用超长随机字符串无限撑大统计维度（内存风险）。
     */
    @GetMapping
    @SentinelResource(value = "hotspot", blockHandler = "hotspotBlockHandler")
    public ApiResponse<Map<String, Object>> hotspot(
            @RequestParam(value = "type", defaultValue = "iphone")
            @Size(max = DemoLimits.MAX_PARAM_LENGTH) String type) {
        return hotspotService.hotspot(type);
    }

    /** 热点参数被限流时抛 ParamFlowException（BlockException 子类），由这里接住 */
    public ApiResponse<Map<String, Object>> hotspotBlockHandler(String type, BlockException ex) {
        return hotspotService.hotspotBlockHandler(type, ex);
    }
}
