package com.wkedong.springboot.basics.web;

import com.wkedong.springboot.basics.service.ProductService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 缓存效果演示接口：用「耗时」和「真实执行次数」两个客观指标说话。
 * <pre>
 * GET    /api/cache/products/1                     首次 ~800ms，第二次 <20ms
 * GET    /api/cache/products/1/self-invocation     永远 ~800ms（自调用导致 @Cacheable 失效）
 * POST   /api/cache/products/1/refresh             强制刷新缓存（@CachePut）
 * DELETE /api/cache/products                       清空缓存（@CacheEvict）
 * GET    /api/cache/stats                          真实执行次数与缓存统计
 * </pre>
 *
 * @author wkedong
 */
@RestController
@RequestMapping("/api/cache")
public class CacheController {

    private final ProductService productService;

    public CacheController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/products/{id}")
    public ApiResponse<Map<String, Object>> product(@PathVariable("id") Long id) {
        long start = System.currentTimeMillis();
        Map<String, Object> data = productService.loadSlowly(id);
        data.put("costMillis", System.currentTimeMillis() - start);
        data.put("cached", "第二次调用同一 id 时 costMillis 应显著变小");
        return ApiResponse.ok(data);
    }

    @GetMapping("/products/{id}/self-invocation")
    public ApiResponse<Map<String, Object>> selfInvocation(@PathVariable("id") Long id) {
        long start = System.currentTimeMillis();
        Map<String, Object> data = productService.loadWithoutCacheBySelfInvocation(id);
        data.put("costMillis", System.currentTimeMillis() - start);
        data.put("cached", false);
        data.put("reason", "同类内部 this.method() 调用绕过 Spring 代理，@Cacheable 不生效");
        return ApiResponse.ok(data);
    }

    @PostMapping("/products/{id}/refresh")
    public ApiResponse<Map<String, Object>> refresh(@PathVariable("id") Long id) {
        return ApiResponse.ok(productService.refresh(id));
    }

    @DeleteMapping("/products")
    public ApiResponse<String> evict() {
        productService.evictAll();
        return ApiResponse.ok("cache cleared");
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("realLoadCount", productService.getLoadCount());
        data.put("hint", "命中缓存时该计数不增长；清空缓存后再次访问会重新增长");
        return ApiResponse.ok(data);
    }
}
