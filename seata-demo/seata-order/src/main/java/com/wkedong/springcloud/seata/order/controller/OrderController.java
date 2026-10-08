package com.wkedong.springcloud.seata.order.controller;

import com.wkedong.springcloud.seata.order.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 订单服务 HTTP 接口（端口 8250）。
 *
 * @author wkedong
 */
@RestController
@RequestMapping("/order")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * 正常下单。
     *
     * @param delayMs 可选，教学用：全局事务挂起毫秒数，便于观察一阶段 undo_log
     */
    @GetMapping("/create")
    public Map<String, Object> create(@RequestParam("productId") Long productId,
                                      @RequestParam("count") Integer count,
                                      @RequestParam(value = "delayMs", defaultValue = "0") long delayMs) {
        return orderService.create(productId, count, delayMs);
    }

    /**
     * 下单后主动失败，用于验证全局回滚。
     *
     * @param delayMs 可选，同上
     * @param failAt  可选，{@code order}（默认）/ {@code inventory}
     */
    @GetMapping("/createWithError")
    public Map<String, Object> createWithError(@RequestParam("productId") Long productId,
                                               @RequestParam("count") Integer count,
                                               @RequestParam(value = "delayMs", defaultValue = "0") long delayMs,
                                               @RequestParam(value = "failAt", defaultValue = "order") String failAt) {
        return orderService.createWithError(productId, count, delayMs, failAt);
    }
}
