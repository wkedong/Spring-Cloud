package com.wkedong.springcloud.seata.order.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 调用库存服务的 Feign 客户端。
 *
 * <p>用固定 {@code url} 直连，不经过注册中心/负载均衡：分布式事务的关注点是「跨服务的一致性」，
 * 与「怎么找到对方」解耦，这样本模块不依赖 Eureka/Nacos 也能单独跑。</p>
 *
 * @author wkedong
 */
@FeignClient(name = "seata-inventory", url = "${demo.inventory.url}")
public interface InventoryClient {

    /** 扣减库存，返回 {productId, count, stock, xid}。 */
    @GetMapping("/inventory/deduct")
    Map<String, Object> deduct(@RequestParam("productId") Long productId,
                               @RequestParam("count") Integer count);

    /** 扣减库存后主动失败，返回 HTTP 500（Feign 侧会抛 FeignException）。 */
    @GetMapping("/inventory/deductWithError")
    Map<String, Object> deductWithError(@RequestParam("productId") Long productId,
                                        @RequestParam("count") Integer count);

    /** 查询库存余量。 */
    @GetMapping("/inventory/stock")
    Map<String, Object> stock(@RequestParam("productId") Long productId);
}
