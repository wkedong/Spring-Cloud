package com.wkedong.springboot.basics.service;

import com.wkedong.springboot.basics.domain.Account;
import com.wkedong.springboot.basics.exception.BusinessException;
import com.wkedong.springboot.basics.exception.ErrorCode;
import com.wkedong.springboot.basics.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 转账服务：事务的教学主战场。
 * <p>
 * 三个可实测的行为：
 * <ol>
 *   <li>{@link #transfer} 正常转账：一个事务里扣款 + 入账，要么都成功要么都不做；</li>
 *   <li>{@link #transferWithFailure} 入账后故意抛异常：验证<b>回滚</b>（两边余额都应回到原值）；</li>
 *   <li>{@link #transferSelfInvocationBroken} <b>自调用陷阱</b>：内部方法上的 @Transactional
 *       不生效，异常发生后已扣的款不会回滚——这是真实项目里最常见的事务失效原因。</li>
 * </ol>
 * 为什么自调用会失效？@Transactional 靠 **Spring AOP 代理**实现：外部调用先经过代理（开启事务），
 * 而 this.method() 是对象内部直接调用，绕过了代理，注解就成了一行注释。
 * 解决办法：把内部方法抽到另一个 bean，或用 AopContext.currentProxy()（不推荐）、
 * TransactionTemplate 编程式事务（最直接）。
 *
 * @author wkedong
 */
@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final AccountRepository accountRepository;

    public TransferService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /** 正常转账：扣款 + 入账在同一事务 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> transfer(Long fromId, Long toId, BigDecimal amount) {
        doTransfer(fromId, toId, amount);
        return snapshot("transfer", fromId, toId, amount);
    }

    /**
     * 转账并在入账后抛异常：用于验证回滚。
     * 期望结果：两边余额都与转账前一致（因为整个事务回滚了）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> transferWithFailure(Long fromId, Long toId, BigDecimal amount) {
        doTransfer(fromId, toId, amount);
        throw new BusinessException("演示用异常：入账后主动失败，观察两边余额是否回滚");
    }

    /**
     * 自调用陷阱：外层方法**没有** @Transactional，它调用同类里的 @Transactional 方法，
     * 由于不经过代理，事务不会开启；扣款已 flush 到数据库，异常不会回滚。
     * 期望结果：from 账户余额被扣掉（错误状态），这正是「事务失效」的现场。
     */
    public Map<String, Object> transferSelfInvocationBroken(Long fromId, Long toId, BigDecimal amount) {
        try {
            doTransferInTransactionalMethod(fromId, toId, amount);
            throw new BusinessException("演示用异常：应为事务失效，扣款不会回滚");
        } catch (BusinessException ex) {
            log.warn("自调用场景捕获异常：{}（注意 from 账户余额已被扣除）", ex.getMessage());
        }
        return snapshot("self-invocation-broken", fromId, toId, amount);
    }

    /** 真正的转账动作 */
    private void doTransfer(Long fromId, Long toId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("转账金额必须大于 0");
        }
        Account from = require(fromId);
        Account to = require(toId);
        if (from.getBalance().compareTo(amount) < 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "余额不足：当前 " + from.getBalance() + "，需要 " + amount);
        }
        from.setBalance(from.getBalance().subtract(amount));
        to.setBalance(to.getBalance().add(amount));
        accountRepository.save(from);
        accountRepository.save(to);
    }

    /** 带 @Transactional 的内部方法（被自调用时注解失效） */
    @Transactional(rollbackFor = Exception.class)
    public void doTransferInTransactionalMethod(Long fromId, Long toId, BigDecimal amount) {
        doTransfer(fromId, toId, amount);
    }

    private Account require(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "账户不存在：id=" + id));
    }

    /** 返回操作后的账户快照，方便 curl 直接观察 */
    public Map<String, Object> snapshot(String action, Long fromId, Long toId, BigDecimal amount) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("action", action);
        data.put("amount", amount);
        for (Long id : new Long[]{fromId, toId}) {
            Account account = require(id);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", account.getId());
            item.put("owner", account.getOwner());
            item.put("balance", account.getBalance());
            data.put("account" + id, item);
        }
        return data;
    }

    public Map<String, Object> allAccounts() {
        Map<String, Object> data = new LinkedHashMap<>();
        for (Account account : accountRepository.findAll()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("owner", account.getOwner());
            item.put("balance", account.getBalance());
            data.put(String.valueOf(account.getId()), item);
        }
        return data;
    }
}
