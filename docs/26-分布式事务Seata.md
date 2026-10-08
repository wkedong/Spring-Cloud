# 26 · 分布式事务与 Seata（AT 模式）

> 模块：`seata-demo/`（`seata-order` 8250、`seata-inventory` 8260），另需一个 **seata-server 1.6.1** 与 **MySQL**。
> 本模块**不依赖**注册中心：order 用固定 URL 直连 inventory；源码基线 **Java 17 / `jakarta.*`**。
> 客户端是 **Seata 2.5.0（Apache 版）**：新增主包名 `org.apache.seata.*`，同时保留 `io.seata.*` 兼容类
> （两者都在 `seata-all-2.5.0.jar` 里，启动日志可见 `Load compatible class io.seata....`），代码里的 `io.seata.*`
> import 不用改也能跑。另有一个升级必踩的坑：**Seata 客户端仍依赖 Jackson 2**，见「常见坑」第 6 条。

## 学什么

一个本地事务只能管住**一个数据源**：`@Transactional` 的边界是「同一个 `DataSource` 上的同一条连接」，它管不住另一次 HTTP 调用，也管不住另一个库：

```java
@Transactional                                   // 边界 = 同一个 DataSource 上的同一条连接
public void createOrder() {
    orderDao.insert(order);                      // 订单库：本地事务管得住
    inventoryClient.deduct(productId, count);    // 库存库：另一个服务、另一条连接 —— 管不着
    throw new IllegalStateException("下单后期失败");  // 订单回滚了，库存却已经扣掉
}
```

跨服务/跨库的一致性有四条路，代价差别很大：

| 方案 | 一致性 | 业务侵入 | 性能与代价 | 适用 |
| --- | --- | --- | --- | --- |
| 2PC / XA | 强一致 | 低（数据库层支持） | 差：资源锁持有到二阶段结束，同步阻塞、协调者单点 | 短事务、同构数据库 |
| TCC | 最终一致 | **高**：每个参与者都要写 try/confirm/cancel | 好，无长事务锁 | 资金、库存等核心链路，能接受手工补偿 |
| Saga | 最终一致 | 中：正向 + 补偿，通常靠状态机 | 好 | 长流程编排；**无隔离性**，中间态对外可见 |
| **AT（本篇）** | 最终一致 + 写隔离 | **低**：加一个 `@GlobalTransactional` | 较好；靠 `undo_log` 与全局锁 | 已有业务代码不想大改 |

AT 的思路：**把「回滚」变成「用 before image 反向补偿」**——业务只写正常逻辑，二阶段回滚由框架用 `undo_log` 自动完成。

## 核心代码

### 1. 发起方：一个注解开启全局事务

```java
@GlobalTransactional(name = "create-order", rollbackFor = Exception.class)
@Transactional(rollbackFor = Exception.class)
public Map<String, Object> create(Long productId, Integer count, long delayMs) {
    long orderId = insertOrder(productId, count, amount);                      // 本地事务：写订单库
    Map<String, Object> inventory = inventoryClient.deduct(productId, count);  // 远程分支：扣库存
    return OrderResult.of(orderId, productId, count, amount, "CREATED", inventory.get("stock"));
}
```

`@GlobalTransactional` 管全局（开事务 + 异常时通知 TC 回滚），`@Transactional` 管订单库这一支的本地事务；缺了后者，订单这支会退化成自动提交。（`service/OrderService.java`）

### 2. 参与方：什么都不用改，但要开本地事务

```java
@Transactional(rollbackFor = Exception.class)   // 参与方只要保住本地事务，业务逻辑不用改
public int deduct(Long productId, Integer count) { return doDeduct(productId, count); }
```

一阶段：执行前取 before image、执行后取 after image，一起写进**同库同事务**的 `undo_log` 再本地提交；二阶段回滚时读 `undo_log`，把 `stock` 改回去。

### 3. XID 必须自己透传（Feign）

XID 只存在当前线程的 `RootContext` 里，HTTP 是新请求，不带过去下游就**不会参与全局事务**：

```java
String xid = RootContext.getXID();
if (xid != null) template.header(RootContext.KEY_XID, xid);   // TX_XID 头，写在 Feign RequestInterceptor 里
```

实测日志：`Feign 透传 XID：192.168.85.52:8091:5161956697001525281 -> /inventory/deduct?productId=1001&count=3`（`feign/FeignXidConfig.java`）。AT 还要求数据源被代理：2.x 用 AOP 包装 `DataSource`（advice 类为 `org.apache.seata.spring.annotation.datasource.SeataAutoDataSourceProxyAdvice`，兼容包名 `io.seata...` 同款），判断姿势见 `config/DataSourceProxyReporter.java`。

## 关键机制

### AT 的两阶段

| | 做什么 | 失败怎么办 |
| --- | --- | --- |
| 一阶段 | 业务 SQL + `undo_log` 写入 + **本地事务提交**（本地锁立即释放）+ 注册分支 | 本地事务回滚，分支上报失败 |
| 二阶段-提交 | 异步删掉 `undo_log`（业务数据早已提交） | 删不掉会重试，不影响一致性 |
| 二阶段-回滚 | 用 `undo_log` 的 before image 反向更新，并校验 after image 是否被改过 | 补偿失败进重试队列，`log_status=1` 防悬挂 |

**一阶段就提交了本地事务**，这是 AT 与 2PC/XA 最大的区别：不长时间持有数据库行锁；代价是「中间态对外可见」，必须靠**全局锁**补上写隔离。

### 全局锁与写隔离

一阶段提交前，RM 要先向 TC 申请该行的全局锁（`lockKey=表名:主键`）。写隔离保证：两个全局事务不能同时改同一行（后到的拿不到全局锁，只能重试/失败）；某个全局事务已提交本地事务、尚未全局提交时，其它全局事务也改不动这一行。

### undo_log 表

```sql
CREATE TABLE IF NOT EXISTS `undo_log` (
  `branch_id`     BIGINT       NOT NULL COMMENT 'branch transaction id',
  `xid`           VARCHAR(128) NOT NULL COMMENT 'global transaction id',
  `context`       VARCHAR(128) NOT NULL COMMENT 'undo_log context,such as serialization',
  `rollback_info` LONGBLOB     NOT NULL COMMENT 'rollback info',
  `log_status`    INT          NOT NULL COMMENT '0:normal status,1:defense status',
  `log_created`   DATETIME(6)  NOT NULL COMMENT 'create datetime',
  `log_modified`  DATETIME(6)  NOT NULL COMMENT 'modify datetime',
  UNIQUE KEY `ux_undo_log` (`xid`, `branch_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='AT transaction mode undo table';
```

**每个参与全局事务的库都要有这张表**，缺一张这个库的分支就回滚不了；`rollback_info` 是序列化后的 `BranchUndoLog`（默认 jackson，即 `org.apache.seata.rm.datasource.undo.BranchUndoLog`——2.5.0 起主包名是 `org.apache.seata.*`），里面是 `beforeImage` / `afterImage`。

### `@GlobalTransactional` 的生效条件与失效场景

生效需要：① `seata.enabled=true`，`tx-service-group` 能在 `vgroup-mapping` 里找到，`grouplist` 指向真实的 **RPC 端口**；② 数据源被 Seata 代理；③ 方法**经过 Spring 代理**（同类自调用不生效）；④ 每个库都有 `undo_log`。

失效场景：**下游没带 XID**（下游变成独立本地事务，全局回滚管不到它）；**分支里吞掉了异常**（TM 收不到失败信号）；**`rollbackFor` 没覆盖抛出的异常类型**；**全局事务外的连接不是代理连接**（见实测）。

### 常见坑

1. **undo_log 表缺失**：报 `Table 'xxx.undo_log' doesn't exist`，一阶段直接失败。
2. **Feign 超时导致全局锁长期持有**：一阶段已提交但 TM 迟迟不结束全局事务，行上的全局锁一直占着，别的全局事务写这一行只能一直重试。
3. **嵌套事务**：内层 `REQUIRES_NEW` 会新开本地事务，但仍在同一全局事务里；内层自己 `try/catch` 掉异常 = 告诉 TM「这一支成功了」，二阶段不会被补偿。
4. **超时与重试**：全局事务默认 60s 超时，超时后 TC 主动回滚并释放全局锁，而客户端业务方法可能仍在跑。
5. **与本地 `@Transactional` 的关系**：本地事务先提交、全局事务后提交/回滚；本地事务回滚 ≠ 全局回滚完成，后者是 TC 驱动的、可能延迟到达的异步动作。
6. **升级到 Boot 4 后必须补 Jackson 2（最容易误判成数据库故障）**：Boot 4 移除了 `com.fasterxml.jackson.*`（换 Jackson 3），而 Seata 客户端还在用 Jackson 2，缺依赖时抛

   ```text
   NoClassDefFoundError: com/fasterxml/jackson/core/JsonProcessingException
   ```

   但**外在表现完全是另一回事**：下单失败、日志里是 `Could not open JDBC Connection for transaction`、返回体 `"xid":null`，很容易顺藤摸瓜去查数据库连接。根 pom 里补上受管版本即可（`jackson2.version` = 2.21.7，Boot 4 BOM 已不管 Jackson 2）：

   ```xml
   <dependency>
       <groupId>com.fasterxml.jackson.core</groupId>
       <artifactId>jackson-databind</artifactId>
       <version>${jackson2.version}</version>
   </dependency>
   ```

   补完记得 **`mvn clean package`**：Boot 4 的 `repackage` 不会刷新 fat jar 里已经打进去的嵌套依赖，只 `package` 会静默复用旧包。

## 动手验证

准备 MySQL（`seata_order` / `seata_inventory` 两个库，**都**建 `undo_log`）与 seata-server；服务端 1.6.1 官方支持 JDK 8/11，17 上容易因反射限制起不来，日志单独指到 `/tmp/seata/logs`：

```bash
mysql -h127.0.0.1 -uroot -p123456 --default-character-set=utf8mb4 < /tmp/seata/init.sql
export JAVA_HOME=$(/usr/libexec/java_home -v 11) && export LOGGING_FILE_PATH=/tmp/seata/logs
cd /tmp/seata/seata && bin/seata-server.sh -h 127.0.0.1 -p 8091 -m file
# 15:19:57.643 INFO --- [main] i.s.core.rpc.netty.NettyServerBootstrap : Server started, service listen port: 8091
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
mvn -pl seata-demo/seata-order,seata-demo/seata-inventory -am package -DskipTests
# 动过依赖（例如补 Jackson 2）时必须 clean：Boot 4 的 repackage 不会刷新 fat jar 里已打进去的嵌套依赖
# mvn -pl seata-demo/seata-order,seata-demo/seata-inventory -am clean package -DskipTests
java -jar seata-demo/seata-inventory/target/seata-inventory-0.0.1-SNAPSHOT.jar   # 8260
java -jar seata-demo/seata-order/target/seata-order-0.0.1-SNAPSHOT.jar           # 8250
```

客户端 `seata.service.grouplist.default` 必须填这个 **RPC 端口 8091**（控制台是 7091，填错连不上）。客户端 2.5.0 连服务端 1.6.1 是实测可行的：
`register TM success. client version:2.5.0, server version:1.6.1`（RM 同款一行）。
确认 AT 挂上了（实测）——`Auto proxy data source 'dataSource' by 'AT' mode.`，以及
`数据源上的 advice：org.apache.seata.spring.annotation.datasource.SeataAutoDataSourceProxyAdvice`：

> ⚠️ 「看不到 `DataSourceProxy` 字样」不等于 AT 没生效：2.x 是 AOP 包装，`DataSourceProxy` 调用时才换进去，且只换在**全局事务上下文**里——库存服务实测全局事务内是 `org.apache.seata.rm.datasource.ConnectionProxy`，全局事务外是 `com.zaxxer.hikari.pool.HikariProxyConnection`（正常）。
> 上面两个类名是 2.5.0 的主包名 `org.apache.seata.*`；同一个 jar 里还保留 `io.seata.*` 兼容类，所以旧教程里的 `io.seata.rm.datasource.ConnectionProxy` 也能对上。

**① 正常路径**——订单 +1 行、库存扣 3 件后为 91，事务结束后 `undo_log` 无残留：

```bash
curl -s "http://127.0.0.1:8250/order/create?productId=1001&count=3"
# {"orderId":13,"productId":1001,"count":3,"amount":30.0,"status":"CREATED","inventoryStock":91,
#  "xid":"192.168.85.52:8091:5161956697001525281"}      HTTP 200
```

```
调用前：stock_1001=94   order_undo_log=0   inv_undo_log=0
调用后：stock_1001=91   order_undo_log=1   inv_undo_log=1   ← 二阶段提交异步，短暂留 1 行
1 秒后：stock_1001=91   order_undo_log=0   inv_undo_log=0   ← 异步删除完成，无残留（最终两端 count(*)=0）
# 订单服务（TM）：订单已写入本地库：orderId=13, xid=192.168.85.52:8091:5161956697001525281
#               全局事务即将提交：orderId=13, inventoryStock=91
# 库存服务（RM）：扣减库存成功：productId=1001, count=3, stockAfter=91
#               branch register success, xid:192.168.85.52:8091:5161956697001525281, branchId:5161956697001525282, lockKeys:inventory:1
# seata-server（TC）侧同款记录是 Register branch successfully / Committing global transaction is successfully done（1.6.1 输出格式）
# 提醒：undo_log 被 Seata 清理干净是设计行为，别拿它当「回滚发生过」的证据——要看业务表的数值
```

**② 回滚路径**——订单已写入、库存已扣减 5 件之后抛异常（`delayMs` 只把全局事务挂住，便于观察一阶段；本次实测用 `delayMs=2000`，要更长的观察窗口就把它调大）：

```bash
curl -s -w '\nHTTP %{http_code}\n' \
  "http://127.0.0.1:8250/order/createWithError?productId=1001&count=5&delayMs=2000"
# {"code":50000,"message":"演示用异常：订单 14 已写入、库存已扣减 5 件（余量 86），随后主动失败","xid":null}
# HTTP 500   ← 注意 "xid":null：响应是在全局事务已经回滚之后组装的
```

一阶段窗口内（全局事务未结束），**库存库的 `undo_log` 已经有行**，订单库还没有（订单本地事务未提交）：

```
[18:21:49.144] seata_inventory.undo_log 有行 -> xid=192.168.85.52:8091:5161956697001525284 | branch_id=5161956697001525285 | log_status=0
[18:21:49.155] 订单服务：createWithError 挂起 2000 ms，用于观察一阶段 undo_log（xid=...5284）
[18:21:51.297] seata_inventory.undo_log 无行      ← 二阶段回滚把它用掉并删除了（xid ...5284 branch ...5285，undo_log deleted with GlobalFinished）
```

`rollback_info` 原文就是补偿依据（`beforeImage.stock=91` / `afterImage.stock=86`，正是「扣 5 件」这一步）：

```json
{"@class":"org.apache.seata.rm.datasource.undo.BranchUndoLog","xid":"...284","branchId":...285,
 "sqlUndoLogs":[{"@class":"...SQLUndoLog","sqlType":"UPDATE","tableName":"inventory",
 "beforeImage":{"name":"stock","value":91}, "afterImage":{"name":"stock","value":86}}]}
```

断言式核对：调用前后**完全一致**，订单被回滚、扣掉的库存被补偿回去；三份日志里的回滚链路（实测原文）：

```
调用前：orders_rows=13  stock_1001=91
调用后：orders_rows=13  stock_1001=91     ← 订单 14 没留下，扣掉的 5 件也还回来了
undo_log：order_undo_log=0  inv_undo_log=0
# 订单服务（TM）：transaction 192.168.85.52:8091:5161956697001525284 will be rollback
#                [192.168.85.52:8091:5161956697001525284] rollback status: Rollbacked
# 库存服务（RM）：Branch Rollbacking: 192.168.85.52:8091:5161956697001525284 5161956697001525285 jdbc:mysql://127.0.0.1:3306/seata_inventory
#                xid ...5284 branch 5161956697001525285, undo_log deleted with GlobalFinished
#                Branch Rollbacked result: PhaseTwo_Rollbacked
# seata-server（TC）：Rollback branch transaction successfully / Rollback global transaction successfully（1.6.1 输出格式）
```

### 实测边界

- 「全局事务外连接是 `HikariProxyConnection`」曾让人误判「AT 没生效」，根因是 Seata 的 advice 只在 `RootContext.requireGlobalLock() || (inGlobalTransaction() && branchType 匹配)` 时才换连接；这不是失败，但一直是实测中最常走的弯路，记下来避免复现。
- **升级后新增的一条弯路：Boot 4 下缺 Jackson 2**（症状是 `Could not open JDBC Connection for transaction` + `xid=null`，根因却是 `NoClassDefFoundError: com/fasterxml/jackson/core/JsonProcessingException`）。别照着数据库连接问题排查，先确认 `seata-demo` 的 pom 里有 `com.fasterxml.jackson.core:jackson-databind`（版本走根 pom 的 `jackson2.version`），并且是 `mvn clean package` 重新打的包。
- **`undo_log` 被清空 ≠ 没回滚**：正常提交与回滚结束后 Seata 都会删掉 `undo_log`（实测两端 `count(*)=0`），判断依据只能是订单/库存业务表的数值。
- **没有实测**（不要当成已验证）：全局锁冲突（两个全局事务并发写同一行）、全局事务超时/悬挂、`log_status=1` 的防悬挂路径、seata-server 的 db/redis 存储模式与集群部署、TCC/Saga 模式。已额外实测的一条：`...createWithError?count=4&failAt=inventory` 让分支自己抛异常时，Feign 收到 500，错误体里带着库存侧看到的 XID（证明 XID 确实传过去了），订单与库存同样回到原值。

## 思考点

1. AT 一阶段就提交本地事务，为什么还能保证「写隔离」？去掉全局锁会出现什么具体的数据错误现场？
2. 全局锁按 `表名:主键` 加锁；`UPDATE ... WHERE status = 0`（不带主键）会退化成什么？为什么 AT 的 SQL 要能定位到主键？
3. `undo_log` 的 after image 校验失败（这一行被别人改过）时 AT 会怎么处理？为什么不能简单强行改回去？
4. 如果库存服务的 `undo_log` 表被误删，业务异常时接口返回、订单库、库存库三者分别是什么状态？
5. 对比 TCC：AT 用「框架补偿」换来低侵入，代价是什么？什么场景你会放弃 AT 改用手写 TCC？
6. 全局事务默认 60s 超时。如果库存服务一次要处理 70s，你会怎么改？超时回滚与「慢请求仍在跑」如何共存？
