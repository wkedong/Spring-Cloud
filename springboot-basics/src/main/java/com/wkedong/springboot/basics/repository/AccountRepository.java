package com.wkedong.springboot.basics.repository;

import com.wkedong.springboot.basics.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 账户仓库。
 *
 * @author wkedong
 */
public interface AccountRepository extends JpaRepository<Account, Long> {
}
