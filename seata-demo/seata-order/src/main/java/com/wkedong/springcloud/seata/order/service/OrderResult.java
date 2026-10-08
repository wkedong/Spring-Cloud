package com.wkedong.springcloud.seata.order.service;

import io.seata.core.context.RootContext;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 下单结果视图（避免在 service 层引入 Web 依赖，用 Map 组装即可）。
 *
 * @author wkedong
 */
final class OrderResult {

    private OrderResult() {
    }

    static Map<String, Object> of(long orderId, Long productId, Integer count,
                                  BigDecimal amount, String status, Object inventoryStock) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", orderId);
        result.put("productId", productId);
        result.put("count", count);
        result.put("amount", amount);
        result.put("status", status);
        result.put("inventoryStock", inventoryStock);
        result.put("xid", RootContext.getXID());
        return result;
    }
}
