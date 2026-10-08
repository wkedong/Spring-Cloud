package com.wkedong.springboot.basics.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Version;
import java.math.BigDecimal;

/**
 * 账户实体：转账事务演示用。
 * <p>
 * 两个教学细节：
 * <ul>
 *   <li>金额用 {@link BigDecimal}，绝不用 double（浮点误差在钱上是事故）；</li>
 *   <li>{@code @Version} 乐观锁：并发扣款时版本不匹配会抛 OptimisticLockException，
 *       避免「余额被覆盖」的丢失更新。</li>
 * </ul>
 *
 * @author wkedong
 */
@Entity
@Table(name = "account")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String owner;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal balance;

    @Version
    private Long version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
