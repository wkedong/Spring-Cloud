package com.wkedong.springboot.basics.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 缓存行为测试：用「耗时」与「真实执行次数」证明缓存是否命中。
 * <p>
 * 缓存类测试的三个要点（教学点）：
 * <ol>
 *   <li>不要断言具体毫秒数（CI 机器慢会假失败），而是断言「量级」和「执行次数」；</li>
 *   <li>把 TTL 调大（本测试 60s），避免测试执行过程中缓存自然过期导致偶发失败；</li>
 *   <li>缓存是<b>跨测试方法共享</b>的（同一个 Spring 上下文），所以每个用例先清缓存。</li>
 * </ol>
 *
 * @author wkedong
 */
@SpringBootTest(properties = "basics.cache.ttl=60s")
class ProductServiceCacheTest {

    @Autowired
    private ProductService productService;

    @Test
    void 第二次调用命中缓存不执行真实查询() {
        productService.evictAll();
        int before = productService.getLoadCount();

        long firstStart = System.currentTimeMillis();
        productService.loadSlowly(1L);
        long firstCost = System.currentTimeMillis() - firstStart;

        long secondStart = System.currentTimeMillis();
        productService.loadSlowly(1L);
        long secondCost = System.currentTimeMillis() - secondStart;

        assertThat(productService.getLoadCount()).isEqualTo(before + 1);
        assertThat(firstCost).isGreaterThanOrEqualTo(700L);
        assertThat(secondCost).isLessThan(100L);
        assertThat(secondCost).isLessThan(firstCost);
    }

    @Test
    void 清除缓存后重新执行真实查询() {
        productService.evictAll();
        productService.loadSlowly(2L);
        int afterFirst = productService.getLoadCount();
        productService.evictAll();
        productService.loadSlowly(2L);
        assertThat(productService.getLoadCount()).isEqualTo(afterFirst + 1);
    }

    @Test
    void 自调用绕过代理缓存失效每次都真实查询() {
        productService.evictAll();
        int before = productService.getLoadCount();
        productService.loadWithoutCacheBySelfInvocation(3L);
        productService.loadWithoutCacheBySelfInvocation(3L);
        // 两次都没命中缓存：说明同类内部调用绕过了 Spring 代理
        assertThat(productService.getLoadCount()).isEqualTo(before + 2);
    }
}
