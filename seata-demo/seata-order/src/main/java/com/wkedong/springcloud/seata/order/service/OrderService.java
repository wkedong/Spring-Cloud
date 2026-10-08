package com.wkedong.springcloud.seata.order.service;

import com.wkedong.springcloud.seata.order.feign.InventoryClient;
import io.seata.core.context.RootContext;
import io.seata.spring.annotation.GlobalTransactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Map;

/**
 * 订单服务：Seata AT 模式的「全局事务发起方（TM + RM）」。
 *
 * <p>{@code @GlobalTransactional} 负责开启全局事务、并在方法抛出异常时通知 TC 发起二阶段回滚；
 * 方法上的 {@code @Transactional} 负责订单库这一支的本地事务。</p>
 *
 * @author wkedong
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final JdbcTemplate jdbcTemplate;
    private final InventoryClient inventoryClient;
    private final BigDecimal unitPrice;

    public OrderService(JdbcTemplate jdbcTemplate,
                        InventoryClient inventoryClient,
                        @Value("${demo.order.unit-price:10.00}") BigDecimal unitPrice) {
        this.jdbcTemplate = jdbcTemplate;
        this.inventoryClient = inventoryClient;
        this.unitPrice = unitPrice;
    }

    /**
     * 正常下单：写订单 -> 远程扣库存 -> 提交全局事务。
     */
    @GlobalTransactional(name = "create-order", rollbackFor = Exception.class)
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(Long productId, Integer count, long delayMs) {
        BigDecimal amount = unitPrice.multiply(BigDecimal.valueOf(count));
        long orderId = insertOrder(productId, count, amount);
        log.info("订单已写入本地库：orderId={}, xid={}", orderId, RootContext.getXID());

        Map<String, Object> inventory = inventoryClient.deduct(productId, count);
        sleepIfNeeded(delayMs, "create");

        log.info("全局事务即将提交：orderId={}, xid={}, inventoryStock={}",
                orderId, RootContext.getXID(), inventory.get("stock"));
        return OrderResult.of(orderId, productId, count, amount, "CREATED", inventory.get("stock"));
    }

    /**
     * 下单后主动失败：订单已写入、库存已扣减，然后抛异常触发全局回滚。
     *
     * @param failAt {@code order}（默认）在订单服务抛异常；{@code inventory} 让库存服务先扣减再抛异常
     */
    @GlobalTransactional(name = "create-order-with-error", rollbackFor = Exception.class)
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createWithError(Long productId, Integer count, long delayMs, String failAt) {
        BigDecimal amount = unitPrice.multiply(BigDecimal.valueOf(count));
        long orderId = insertOrder(productId, count, amount);
        log.info("订单已写入本地库：orderId={}, xid={}", orderId, RootContext.getXID());

        Map<String, Object> inventory;
        if ("inventory".equalsIgnoreCase(failAt)) {
            inventory = inventoryClient.deductWithError(productId, count);
        } else {
            inventory = inventoryClient.deduct(productId, count);
        }
        sleepIfNeeded(delayMs, "createWithError");

        throw new IllegalStateException("演示用异常：订单 " + orderId + " 已写入、库存已扣减 " + count
                + " 件（余量 " + inventory.get("stock") + "），随后主动失败");
    }

    private long insertOrder(Long productId, Integer count, BigDecimal amount) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO orders (user_id, product_id, count, amount, status) VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, 1L);
            ps.setLong(2, productId);
            ps.setInt(3, count);
            ps.setBigDecimal(4, amount);
            ps.setString(5, "CREATED");
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("插入订单后未拿到自增主键");
        }
        return key.longValue();
    }

    /**
     * 教学用：把全局事务挂住一段时间，便于在「一阶段已提交、二阶段尚未到达」的窗口里观察 undo_log。
     */
    private void sleepIfNeeded(long delayMs, String scene) {
        if (delayMs <= 0) {
            return;
        }
        log.info("{} 挂起 {} ms，用于观察一阶段 undo_log（xid={}）", scene, delayMs, RootContext.getXID());
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("挂起被中断", e);
        }
    }
}
