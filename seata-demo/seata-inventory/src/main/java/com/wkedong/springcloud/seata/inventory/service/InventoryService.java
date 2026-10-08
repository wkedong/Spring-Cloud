package com.wkedong.springcloud.seata.inventory.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.seata.core.context.RootContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * 库存服务：Seata AT 模式的「分支事务参与方（RM）」。
 *
 * <p>每个写方法都带本地事务注解 {@code @Transactional}：
 * Seata 的 {@code DataSourceProxy} 会在本地事务提交前，把「修改前镜像 + 修改后镜像」写进同库的
 * {@code undo_log} 表，然后才提交。这样一阶段（本地提交）与二阶段回滚（用 undo_log 补偿）才成立。</p>
 *
 * @author wkedong
 */
@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public InventoryService(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    /**
     * 查询库存余量。
     */
    public int queryStock(Long productId) {
        Integer stock = jdbcTemplate.query(
                "SELECT stock FROM inventory WHERE product_id = ?",
                rs -> rs.next() ? rs.getInt(1) : null, productId);
        if (stock == null) {
            throw new IllegalStateException("商品不存在：productId=" + productId);
        }
        return stock;
    }

    /**
     * 正常扣减库存，返回扣减后的余量。
     */
    @Transactional(rollbackFor = Exception.class)
    public int deduct(Long productId, Integer count) {
        int stockAfter = doDeduct(productId, count);
        log.info("扣减库存成功：productId={}, count={}, stockAfter={}", productId, count, stockAfter);
        logConnectionInsideGlobalTransaction();
        return stockAfter;
    }

    /**
     * 先扣减、再抛异常：用于演示「分支事务自己失败」时全局事务如何回滚。
     */
    @Transactional(rollbackFor = Exception.class)
    public int deductWithError(Long productId, Integer count) {
        int stockAfter = doDeduct(productId, count);
        log.warn("演示用异常：库存已在本事务内扣减 {} 件（productId={}, stockAfter={}），随后主动抛出异常",
                count, productId, stockAfter);
        throw new IllegalStateException("演示用异常：库存已扣减 " + count + " 件，随后主动失败（productId=" + productId + "）");
    }

    private int doDeduct(Long productId, Integer count) {
        if (count == null || count <= 0) {
            throw new IllegalArgumentException("count 必须大于 0");
        }
        int stock = queryStock(productId);
        if (stock < count) {
            throw new IllegalStateException("库存不足：productId=" + productId + "，余量 " + stock + "，需要 " + count);
        }
        int updated = jdbcTemplate.update(
                "UPDATE inventory SET stock = stock - ? WHERE product_id = ? AND stock >= ?",
                count, productId, count);
        if (updated == 0) {
            throw new IllegalStateException("扣减库存失败（并发冲突或库存不足）：productId=" + productId);
        }
        return stock - count;
    }

    /**
     * 在全局事务内旁证 AT 已生效：此时从 DataSource 拿到的连接应该是
     * {@code io.seata.rm.datasource.ConnectionProxy}，而不是 Hikari 的原生连接。
     */
    private void logConnectionInsideGlobalTransaction() {
        if (!RootContext.inGlobalTransaction()) {
            log.info("当前不在全局事务内（xid={}），连接不会被 Seata 代理", RootContext.getXID());
            return;
        }
        try (Connection connection = dataSource.getConnection()) {
            log.info("全局事务内连接类型：{}（xid={}）", connection.getClass().getName(), RootContext.getXID());
        } catch (SQLException e) {
            log.warn("探测连接类型失败：{}", e.getMessage());
        }
    }
}
