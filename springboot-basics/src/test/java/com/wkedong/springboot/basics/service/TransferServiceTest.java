package com.wkedong.springboot.basics.service;

import com.wkedong.springboot.basics.domain.Account;
import com.wkedong.springboot.basics.exception.BusinessException;
import com.wkedong.springboot.basics.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 事务行为测试：把「回滚」和「自调用失效」变成可断言的客观事实。
 * <p>
 * 注意这里**没有**给测试方法加 @Transactional：本测试要验证的正是服务层自己的事务边界，
 * 若测试再包一层事务，会把真实行为掩盖掉。数据一致性靠 @BeforeEach 重置余额保证。
 *
 * @author wkedong
 */
@SpringBootTest
class TransferServiceTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    @BeforeEach
    void resetAccounts() {
        // 注意：这里必须「先查出来再改」（托管实体更新），不能 new 一个带 id 的对象直接 save：
        // 带 @Version 的实体在 save 时会因为 version 为 null 被判定为「新对象」而走 persist，
        // 结果余额根本没被重置（这是本模块踩过的真实坑，见 docs）。
        setBalance(1L, "1000.00");
        setBalance(2L, "500.00");
    }

    @Test
    void 正常转账两边余额同时变化() {
        Map<String, Object> snapshot = transferService.transfer(1L, 2L, new BigDecimal("100"));
        assertThat(balanceOf(1L)).isEqualByComparingTo("900.00");
        assertThat(balanceOf(2L)).isEqualByComparingTo("600.00");
        assertThat(snapshot).containsKey("account1");
    }

    @Test
    void 入账后抛异常时整个事务回滚() {
        assertThatThrownBy(() -> transferService.transferWithFailure(1L, 2L, new BigDecimal("100")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("演示用异常");
        // 关键断言：失败发生在「已扣款 + 已入账」之后，但事务回滚，两边余额必须保持原值
        assertThat(balanceOf(1L)).isEqualByComparingTo("1000.00");
        assertThat(balanceOf(2L)).isEqualByComparingTo("500.00");
    }

    @Test
    void 余额不足时抛业务异常且不改变数据() {
        assertThatThrownBy(() -> transferService.transfer(2L, 1L, new BigDecimal("99999")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("余额不足");
        assertThat(balanceOf(1L)).isEqualByComparingTo("1000.00");
        assertThat(balanceOf(2L)).isEqualByComparingTo("500.00");
    }

    @Test
    void 自调用导致事务失效扣款不回滚() {
        transferService.transferSelfInvocationBroken(1L, 2L, new BigDecimal("100"));
        // 这就是「事务失效」的现场：异常被捕获，但扣款已经提交，数据处于错误状态。
        // 修复方式：把 doTransferInTransactionalMethod 抽到另一个 Bean，或改用 TransactionTemplate。
        assertThat(balanceOf(1L)).isEqualByComparingTo("900.00");
        assertThat(balanceOf(2L)).isEqualByComparingTo("600.00");
    }

    private void setBalance(Long id, String balance) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("账户不存在，data.sql 未执行？id=" + id));
        account.setBalance(new BigDecimal(balance));
        accountRepository.saveAndFlush(account);
    }

    private BigDecimal balanceOf(Long id) {
        return accountRepository.findById(id).map(Account::getBalance).orElseThrow(IllegalStateException::new);
    }
}
