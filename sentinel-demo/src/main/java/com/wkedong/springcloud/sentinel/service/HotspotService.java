package com.wkedong.springcloud.sentinel.service;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.wkedong.springcloud.sentinel.web.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 热点参数限流演示（{@code ParamFlowRule} + {@code @SentinelResource}）。
 * <p>
 * 为什么需要它：整资源限流是「一刀切」——爆款商品把流量打满时，
 * 对资源限流会连同长尾商品一起拒掉；热点参数限流按「参数值」分别计数，
 * 只掐那个热的，其它值完全不受影响。
 * <p>
 * 注意：热点参数是 Sentinel 里唯一必须配合 {@code @SentinelResource} 使用的规则类型——
 * 因为只有注解/API 入口才知道「方法参数是什么」，URL 资源拿不到参数值。
 * <p>
 * 例外项（{@code ParamFlowItem}）可以给特定参数值单独配阈值，例如给 VIP 用户或内部压测流量放行。
 *
 * @author wkedong
 */
@Service
public class HotspotService {

    private static final Logger log = LoggerFactory.getLogger(HotspotService.class);

    /** 被热点参数规则拦下的次数 */
    private static final AtomicInteger BLOCKED = new AtomicInteger();

    /**
     * 按参数值限流：{@code type=iphone} 限 2 QPS，{@code type=macbook} 等其它值各算一份配额。
     * <p>
     * 这里的 {@code type} 就是 {@code ParamFlowRule.paramIdx=0} 指向的第 0 个参数。
     */
    public ApiResponse<Map<String, Object>> hotspot(String type) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("resource", "hotspot");
        data.put("explain", "热点参数放行：参数值 " + type + " 在本秒内的配额还没用完");
        data.put("type", type);
        data.put("paramIdx", 0);
        return ApiResponse.ok(data);
    }

    /**
     * 热点参数被拦截：Sentinel 抛的是 {@code ParamFlowException}
     * （{@code FlowException} 的子类，同属 {@code BlockException}），
     * 所以 blockHandler 的参数写 {@link BlockException} 能同时接住普通流控和热点参数限流。
     */
    public ApiResponse<Map<String, Object>> hotspotBlockHandler(String type, BlockException ex) {
        int blocked = BLOCKED.incrementAndGet();
        log.warn("=== hotspot 参数值 {} 被热点参数规则拦截（第 {} 次）===", type, blocked);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("resource", "hotspot");
        data.put("explain", "blockHandler 触发：参数值 " + type + " 超过 2 QPS，被 ParamFlowRule 拦下");
        data.put("type", type);
        data.put("blockedCount", blocked);
        data.put("blockException", ex.getClass().getSimpleName());
        ApiResponse<Map<String, Object>> response = ApiResponse.fail(7001, "热点参数限流（ParamFlowException）");
        response.setData(data);
        return response;
    }

    /** 供 /sentinel/status 使用 */
    public Map<String, Object> counters() {
        Map<String, Object> counters = new LinkedHashMap<>();
        counters.put("hotspot 被拦次数", BLOCKED.get());
        return counters;
    }
}
