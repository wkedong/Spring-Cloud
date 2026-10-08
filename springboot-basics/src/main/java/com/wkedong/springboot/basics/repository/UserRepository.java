package com.wkedong.springboot.basics.repository;

import com.wkedong.springboot.basics.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA 仓库：接口即实现（运行时生成代理）。
 * <p>
 * 教学点：方法名即查询语句（findByNameContaining、findByAgeGreaterThan 等派生子查询），
 * 复杂查询再退回 @Query。这里只演示最基础的用法。
 *
 * @author wkedong
 */
public interface UserRepository extends JpaRepository<User, Long> {

    List<User> findByNameContaining(String name);

    boolean existsByEmail(String email);
}
