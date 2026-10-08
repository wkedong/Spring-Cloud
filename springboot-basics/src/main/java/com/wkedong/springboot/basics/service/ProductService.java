package com.wkedong.springboot.basics.service;

import com.wkedong.springboot.basics.config.CacheConfig;
import com.wkedong.springboot.basics.exception.BusinessException;
import com.wkedong.springboot.basics.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 商品服务：专门用来「看见」缓存效果。
 * <p>
 * {@link #loadSlowly(Long)} 每次真实执行都会睡 800ms 并计入 loadCount；
 * 加缓存后第二次调用（缓存未过期时）应当是「毫秒级返回且 loadCount 不变」。
 * <p>
 * 教学重点——三种注解的区别：
 * <pre>
 * @Cacheable  先查缓存，命中则不执行方法（用于查询）
 * @CachePut   总是执行方法，并把返回值写入缓存（用于更新后保持缓存新鲜）
 * @CacheEvict 执行后删除缓存（用于删除/更新，让下次读取重建）
 * </pre>
 * 以及**经典陷阱**：同类内部调用（this.loadSlowly）不会走代理，缓存与事务注解全部失效，
 * 见 {@link #loadWithoutCacheBySelfInvocation(Long)}。
 *
 * @author wkedong
 */
@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    /** 模拟数据库的静态数据 */
    private static final Map<Long, String> PRODUCT_NAMES = new LinkedHashMap<Long, String>() {{
        put(1L, "Spring Boot 实战");
        put(2L, "Spring Cloud 微服务");
        put(3L, "JVM 性能调优");
    }};

    /** 真实执行次数（教学观察用）：缓存命中时不会增长 */
    private final AtomicInteger loadCount = new AtomicInteger();

    /** 模拟慢查询：真实项目里这里是 DB/远程调用 */
    @Cacheable(cacheNames = CacheConfig.CACHE_PRODUCTS, key = "#id")
    public Map<String, Object> loadSlowly(Long id) {
        loadCount.incrementAndGet();
        sleepQuietly(800);
        String name = PRODUCT_NAMES.get(id);
        if (name == null) {
            // 查不到就抛异常：不缓存 null，也就意味着「不存在的 id」每次都会打到慢查询（缓存穿透）
            throw new BusinessException(ErrorCode.NOT_FOUND, "商品不存在：id=" + id);
        }
        return describe(id, name, false);
    }

    /** 更新缓存（@CachePut 语义演示） */
    @CachePut(cacheNames = CacheConfig.CACHE_PRODUCTS, key = "#id")
    public Map<String, Object> refresh(Long id) {
        loadCount.incrementAndGet();
        String name = PRODUCT_NAMES.get(id);
        if (name == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "商品不存在：id=" + id);
        }
        return describe(id, name, true);
    }

    /** 清空商品缓存 */
    @CacheEvict(cacheNames = CacheConfig.CACHE_PRODUCTS, allEntries = true)
    public void evictAll() {
        log.info("商品缓存已清空（loadCount 保持 {}）", loadCount.get());
    }

    /**
     * 自调用陷阱演示：本方法内部直接调 this.loadSlowly(id)，
     * 不经过 Spring 代理 → @Cacheable 不生效 → 每次都真实执行（耗时会稳定在 800ms 以上）。
     */
    public Map<String, Object> loadWithoutCacheBySelfInvocation(Long id) {
        Map<String, Object> result = loadSlowly(id);
        result.put("selfInvocation", true);
        return result;
    }

    /** 真实执行次数，用于验证缓存是否命中 */
    public int getLoadCount() {
        return loadCount.get();
    }

    private Map<String, Object> describe(Long id, String name, boolean refreshed) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("name", name);
        data.put("price", new BigDecimal("59.00").add(BigDecimal.valueOf(id * 10L)));
        data.put("refreshed", refreshed);
        data.put("loadCount", getLoadCount());
        return data;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
