package com.wkedong.springcloud.seata.inventory.controller;

import com.wkedong.springcloud.seata.inventory.service.InventoryService;
import io.seata.core.context.RootContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 库存服务 HTTP 接口（端口 8260）。
 *
 * <p>响应里带上 {@code xid}（{@code RootContext.getXID()}），可以直观看到：
 * 由订单服务发起的全局事务，其 XID 会通过 Feign 请求头 {@code TX_XID} 传播到这里，
 * 因此本服务的分支事务才能挂到同一个全局事务上。</p>
 *
 * @author wkedong
 */
@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /** 查询库存余量。 */
    @GetMapping("/stock")
    public Map<String, Object> stock(@RequestParam("productId") Long productId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("productId", productId);
        result.put("stock", inventoryService.queryStock(productId));
        result.put("xid", RootContext.getXID());
        return result;
    }

    /** 正常扣减库存，返回扣减后的余量。 */
    @GetMapping("/deduct")
    public Map<String, Object> deduct(@RequestParam("productId") Long productId,
                                      @RequestParam("count") Integer count) {
        int stockAfter = inventoryService.deduct(productId, count);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("productId", productId);
        result.put("count", count);
        result.put("stock", stockAfter);
        result.put("xid", RootContext.getXID());
        return result;
    }

    /** 先扣减、再抛异常，用于触发全局回滚。 */
    @GetMapping("/deductWithError")
    public Map<String, Object> deductWithError(@RequestParam("productId") Long productId,
                                               @RequestParam("count") Integer count) {
        int stockAfter = inventoryService.deductWithError(productId, count);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("productId", productId);
        result.put("count", count);
        result.put("stock", stockAfter);
        result.put("xid", RootContext.getXID());
        return result;
    }
}
