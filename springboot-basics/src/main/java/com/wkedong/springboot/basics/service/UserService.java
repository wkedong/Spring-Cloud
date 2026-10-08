package com.wkedong.springboot.basics.service;

import com.wkedong.springboot.basics.config.CacheConfig;
import com.wkedong.springboot.basics.domain.User;
import com.wkedong.springboot.basics.exception.BusinessException;
import com.wkedong.springboot.basics.exception.ErrorCode;
import com.wkedong.springboot.basics.repository.UserRepository;
import com.wkedong.springboot.basics.web.dto.UserCreateRequest;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户服务：缓存注解与事务注解的组合示例。
 * <p>
 * 「查询走缓存、写入清缓存」是缓存使用的基本纪律：
 * 更新/删除时用 {@code @CacheEvict} 而不是 {@code @CachePut}，
 * 因为删掉让下次读取重建，比「猜着更新」更不容易产生不一致。
 *
 * @author wkedong
 */
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 查询单个用户：结果进 users 缓存。
     * 注意：查不到时抛业务异常而不是返回 null——Caffeine 缓存配置了 allowNullValues=false，
     * 返回 null 会导致缓存层报错（详见 docs 里「缓存穿透」的讨论）。
     */
    @Cacheable(cacheNames = CacheConfig.CACHE_USERS, key = "#id")
    @Transactional(readOnly = true)
    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在：id=" + id));
    }

    @Transactional(readOnly = true)
    public List<User> list() {
        return userRepository.findAll();
    }

    @Transactional(rollbackFor = Exception.class)
    public User create(UserCreateRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("邮箱已存在：" + request.getEmail());
        }
        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setAge(request.getAge());
        return userRepository.save(user);
    }

    /** 修改用户：清掉该用户的缓存，避免脏读 */
    @CacheEvict(cacheNames = CacheConfig.CACHE_USERS, key = "#id")
    @Transactional(rollbackFor = Exception.class)
    public User rename(Long id, String name) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在：id=" + id));
        user.setName(name);
        return userRepository.save(user);
    }

    @CacheEvict(cacheNames = CacheConfig.CACHE_USERS, allEntries = true)
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (!userRepository.existsById(id)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在：id=" + id);
        }
        userRepository.deleteById(id);
    }
}
