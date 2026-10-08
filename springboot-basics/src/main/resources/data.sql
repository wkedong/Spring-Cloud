-- ============================================================================
-- 初始化数据（H2 内存库）
-- 执行时机由 spring.jpa.defer-datasource-initialization=true 保证：
-- 先由 Hibernate 按实体建表，再执行本脚本；否则会报「表不存在」。
-- 生产环境请用 Flyway/Liquibase 管理结构与数据变更（见 config 模块示例）。
-- ============================================================================

INSERT INTO sys_user (name, email, phone, age, created_at) VALUES
 ('张三', 'zhangsan@example.com', '13800138001', 28, CURRENT_TIMESTAMP),
 ('李四', 'lisi@example.com', '13900139002', 34, CURRENT_TIMESTAMP);

-- 转账事务演示用的两个账户
INSERT INTO account (owner, balance, version) VALUES
 ('张三', 1000.00, 0),
 ('李四', 500.00, 0);

-- ============================================================================
-- 显式指定主键后，必须把自增序列推到「已有数据之后」：
-- H2/MySQL 不会因为插入了 id=1、2 而自动前进，否则后续 JPA 插入新记录时
-- 会再次生成 id=1 → 主键冲突（Duplicate entry '1' for key 'PRIMARY'）。
-- 这是脚本初始化 + 自增主键组合的经典坑。
-- ============================================================================
ALTER TABLE sys_user ALTER COLUMN id RESTART WITH 100;
ALTER TABLE account ALTER COLUMN id RESTART WITH 100;
