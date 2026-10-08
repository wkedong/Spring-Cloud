package com.wkedong.springcloud.serviceconsumer.ribbon.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.DefaultResponse;
import org.springframework.cloud.client.loadbalancer.EmptyResponse;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.loadbalancer.core.NoopServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import reactor.core.publisher.Mono;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 自定义负载均衡器：按请求头做**灰度路由**，没有灰度标记时退回轮询。
 * <p>
 * 这是「自定义负载均衡」最有价值的实战场景：新版本先只放行内部测试流量（打标 v2），
 * 观察稳定后再全量。实现要点：
 * <ol>
 *   <li>实例筛选靠**注册元数据**：producer 各实例在 {@code eureka.instance.metadata-map.version} 里声明自己的版本；</li>
 *   <li>灰度标记从当前 HTTP 请求头 {@code X-Gray-Version} 读取（RequestContextHolder 是 ThreadLocal，
 *       所以只能在处理请求的线程里取——这也是 LoadBalancer 自定义策略的常见限制）；</li>
 *   <li>没有匹配实例时要**降级为普通轮询**而不是报错：灰度标记不应该让服务整体不可用；</li>
 *   <li>要能在日志里看到「为什么选中了这个实例」，否则排查灰度问题会非常痛苦。</li>
 * </ol>
 *
 * @author wkedong
 */
public class GrayHeaderLoadBalancer implements ReactorServiceInstanceLoadBalancer {

    private static final Logger logger = LoggerFactory.getLogger(GrayHeaderLoadBalancer.class);
    private static final String GRAY_HEADER = "X-Gray-Version";
    private static final String VERSION_METADATA_KEY = "version";

    private final String serviceId;
    private final ObjectProvider<ServiceInstanceListSupplier> supplierProvider;
    private final AtomicInteger position = new AtomicInteger();

    public GrayHeaderLoadBalancer(ObjectProvider<ServiceInstanceListSupplier> supplierProvider, String serviceId) {
        this.supplierProvider = supplierProvider;
        this.serviceId = serviceId;
    }

    @Override
    public Mono<Response<ServiceInstance>> choose(Request request) {
        ServiceInstanceListSupplier supplier = supplierProvider.getIfAvailable(NoopServiceInstanceListSupplier::new);
        return supplier.get(request).next().map(instances -> select(instances));
    }

    private Response<ServiceInstance> select(List<ServiceInstance> instances) {
        if (instances == null || instances.isEmpty()) {
            logger.warn("服务 {} 没有可用实例", serviceId);
            return new EmptyResponse();
        }
        String grayVersion = currentGrayVersion();
        if (grayVersion != null) {
            for (ServiceInstance instance : instances) {
                if (grayVersion.equals(instance.getMetadata().get(VERSION_METADATA_KEY))) {
                    logger.info("灰度路由命中：{} -> {}:{} (metadata.version={})",
                            serviceId, instance.getHost(), instance.getPort(), grayVersion);
                    return new DefaultResponse(instance);
                }
            }
            logger.warn("灰度标记 {} 在 {} 中没有匹配实例，退化为轮询（共 {} 个实例）",
                    grayVersion, serviceId, instances.size());
        }
        ServiceInstance instance = roundRobin(instances);
        logger.info("轮询选中：{} -> {}:{} (metadata.version={})", serviceId, instance.getHost(), instance.getPort(),
                instance.getMetadata().get(VERSION_METADATA_KEY));
        return new DefaultResponse(instance);
    }

    private ServiceInstance roundRobin(List<ServiceInstance> instances) {
        int index = Math.abs(position.getAndIncrement() % instances.size());
        return instances.get(index);
    }

    private String currentGrayVersion() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes) {
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
            String value = request.getHeader(GRAY_HEADER);
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }
}
