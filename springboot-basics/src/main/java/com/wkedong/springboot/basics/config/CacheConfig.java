package com.wkedong.springboot.basics.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 缓存配置：Caffeine 本地缓存。
 * <p>
 * 教学点：
 * <ul>
 *   <li>Spring 只提供**缓存抽象**（@Cacheable 等注解），实现可换：Caffeine（本地）/
 *       Redis（分布式）/ 多级缓存。换实现不改业务代码。</li>
 *   <li>Caffeine 的关键参数：{@code expireAfterWrite}（写入后过期）、{@code maximumSize}
 *       （容量上限，超出按 W-TinyLFU 淘汰）、{@code recordStats}（开启命中率统计，
 *       对应 /actuator/caches 与 /actuator/metrics/cache.gets）。</li>
 *   <li>参数的来源是我们自己的 {@link BasicsProperties}——「配置驱动行为」的完整闭环。</li>
 * </ul>
 *
 * @author wkedong
 */
@Configuration
public class CacheConfig {

    public static final String CACHE_PRODUCTS = "products";
    public static final String CACHE_USERS = "users";

    @Bean
    public CacheManager cacheManager(BasicsProperties properties) {
        CaffeineCacheManager manager = new CaffeineCacheManager(CACHE_PRODUCTS, CACHE_USERS);
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(properties.getCache().getTtl().toMillis(), TimeUnit.MILLISECONDS)
                .maximumSize(properties.getCache().getMaxSize())
                .recordStats());
        // 不允许缓存 null：避免「缓存穿透」时把空值也缓存住（教学讨论点，见 docs）
        manager.setAllowNullValues(false);
        return manager;
    }
}
