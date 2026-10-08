package com.wkedong.springcloud.nacos.controller;

import com.wkedong.springcloud.nacos.service.DiscoveryService;
import com.wkedong.springcloud.nacos.web.ApiResponse;
import com.wkedong.springcloud.nacos.web.dto.InstanceView;
import com.wkedong.springcloud.nacos.web.dto.LbCallView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 服务注册发现相关的端点。
 * <p>
 * 命名约定：{@code /nacos/instance-info} 是「被调用方自述」，
 * 会被 lb-call 通过服务名回调，因此它必须足够轻（不查库、不调外部服务）。
 *
 * @author wkedong
 */
@RestController
@RequestMapping("/nacos")
public class DiscoveryController {

    private final DiscoveryService discoveryService;

    public DiscoveryController(DiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    /**
     * 本实例自述：我是谁、监听在哪个端口、注册元数据是什么。
     * <p>
     * 多实例演示时，这个端点的返回端口就是「这一次请求命中了哪台机器」的答案。
     */
    @GetMapping("/instance-info")
    public ApiResponse<InstanceView> instanceInfo() {
        return ApiResponse.ok(discoveryService.self());
    }

    /**
     * 注册表里当前有哪些服务。
     */
    @GetMapping("/discovery/services")
    public ApiResponse<List<String>> services() {
        return ApiResponse.ok(discoveryService.listServices());
    }

    /**
     * 某个服务的全部实例。
     *
     * @param serviceName 服务名，例如 nacos-demo
     */
    @GetMapping("/discovery/instances")
    public ApiResponse<List<InstanceView>> instances(@RequestParam("serviceName") String serviceName) {
        return ApiResponse.ok(discoveryService.listInstances(serviceName));
    }

    /**
     * 只观察「客户端视角」的负载均衡选择结果（会消耗一次轮询位置，别和 lb-call 混用）。
     *
     * @param serviceName 服务名
     */
    @GetMapping("/discovery/lb-pick")
    public ApiResponse<InstanceView> lbPick(@RequestParam(value = "serviceName", defaultValue = "nacos-demo")
                                                    String serviceName) {
        InstanceView picked = discoveryService.pickByLoadBalancer(serviceName);
        return picked == null
                ? ApiResponse.<InstanceView>fail(404, "注册表里没有可用实例：" + serviceName)
                : ApiResponse.ok(picked);
    }

    /**
     * 按服务名调用自己，返回本次命中的实例端口与累计命中分布。
     * <p>
     * 这条请求走的是 {@code http://nacos-demo/nacos/instance-info}：
     * 服务名在注册中心被解析成某个实例的 host:port，代码里没有任何 IP。
     */
    @GetMapping("/lb-call")
    public ApiResponse<LbCallView> lbCall() {
        return ApiResponse.ok(discoveryService.lbCall());
    }
}
