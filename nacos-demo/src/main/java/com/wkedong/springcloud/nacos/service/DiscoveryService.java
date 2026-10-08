package com.wkedong.springcloud.nacos.service;

import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import com.wkedong.springcloud.nacos.web.ApiResponse;
import com.wkedong.springcloud.nacos.web.dto.InstanceView;
import com.wkedong.springcloud.nacos.web.dto.LbCallView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.context.ApplicationListener;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 服务注册发现的「读侧」封装：查服务、查实例、按服务名调用。
 * <p>
 * 设计取舍：
 * <ul>
 *   <li>Controller 只做参数与响应包装，注册中心的细节（DiscoveryClient、
 *       LoadBalancerClient、本机端口）全部收在这个 service 里，换注册中心不用改控制器；</li>
 *   <li>本机端口通过监听 {@link WebServerInitializedEvent} 拿到——这是「这个进程到底监听在哪」
 *       的唯一可靠来源（{@code server.port} 只说明你「要求」监听哪个端口），
 *       也是多实例演示里区分「我是 8210 还是 8211」的依据。</li>
 * </ul>
 *
 * @author wkedong
 */
@Service
public class DiscoveryService implements ApplicationListener<WebServerInitializedEvent> {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryService.class);

    /** 被 lb-call 调用的自述端点路径：调用方写服务名 + 这个路径 */
    public static final String SELF_INFO_PATH = "/nacos/instance-info";

    private final DiscoveryClient discoveryClient;
    private final LoadBalancerClient loadBalancerClient;
    private final RestTemplate loadBalancedRestTemplate;
    private final NacosDiscoveryProperties nacosDiscoveryProperties;

    /** 本实例累计的命中次数：端口 → 次数 */
    private final Map<String, Integer> hitsByPort = new LinkedHashMap<String, Integer>();
    private final AtomicInteger lbCallSeq = new AtomicInteger();

    @Value("${spring.application.name:nacos-demo}")
    private String applicationName;

    /** 真实监听端口，由 WebServerInitializedEvent 回填 */
    private volatile int localPort = -1;

    public DiscoveryService(DiscoveryClient discoveryClient,
                            LoadBalancerClient loadBalancerClient,
                            RestTemplate loadBalancedRestTemplate,
                            NacosDiscoveryProperties nacosDiscoveryProperties) {
        this.discoveryClient = discoveryClient;
        this.loadBalancerClient = loadBalancerClient;
        this.loadBalancedRestTemplate = loadBalancedRestTemplate;
        this.nacosDiscoveryProperties = nacosDiscoveryProperties;
    }

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        this.localPort = event.getWebServer().getPort();
        log.info("本实例监听端口已确认：{}（服务名 {}）", localPort, applicationName);
    }

    public int localPort() {
        return localPort;
    }

    /**
     * 注册表里当前有哪些服务（只统计本客户端订阅到的，默认包含自己）。
     */
    public List<String> listServices() {
        return discoveryClient.getServices();
    }

    /**
     * 某个服务的全部实例。
     *
     * @param serviceName 服务名；为空时返回空列表
     */
    public List<InstanceView> listInstances(String serviceName) {
        List<InstanceView> views = new ArrayList<InstanceView>();
        if (serviceName == null || serviceName.trim().isEmpty()) {
            return views;
        }
        for (ServiceInstance instance : discoveryClient.getInstances(serviceName.trim())) {
            views.add(InstanceView.from(serviceName, instance));
        }
        return views;
    }

    /**
     * 本进程自述：不查注册中心也能回答「我是谁、监听在哪」。
     * <p>
     * 额外做一件事：去注册表里按端口找到「我自己那一条」，把注册中心视角的
     * instanceId 与元数据一并带上——这样两端信息对得上，方便排查注册异常。
     */
    public InstanceView self() {
        InstanceView view = new InstanceView();
        view.setServiceName(applicationName);
        view.setHost(localHost());
        view.setPort(localPort);
        view.setLocal(true);
        // 先放本地配置里的元数据（注册时用的就是它），再尝试用注册中心里的真实数据覆盖
        Map<String, String> metadata = nacosDiscoveryProperties.getMetadata();
        if (metadata != null) {
            view.getMetadata().putAll(metadata);
        }
        try {
            for (ServiceInstance instance : discoveryClient.getInstances(applicationName)) {
                if (instance.getPort() == localPort) {
                    // 注册中心里的 host 才是「别人能访问到的地址」：
                    // Nacos 默认取网卡 IP，而 InetAddress.getLocalHost() 可能给到 127.0.0.1，
                    // 两边不一致会让「自述地址」和「被调用时用的地址」对不上。
                    view.setHost(instance.getHost());
                    view.setInstanceId(instance.getInstanceId());
                    view.getMetadata().putAll(instance.getMetadata());
                    break;
                }
            }
        } catch (Exception ex) {
            // 注册中心暂时不可用也不该让自述端点 500：降级为「只有本地信息」
            log.warn("查询注册表失败，自述信息降级：{}", ex.getMessage());
        }
        view.setUri("http://" + view.getHost() + ":" + view.getPort());
        return view;
    }

    /**
     * 按服务名调用自己，返回「谁响应了这次请求」。
     * <p>
     * 关键点：URL 里写的是服务名 {@code http://nacos-demo/...}，
     * 真实的 host:port 由 LoadBalancer 在请求发出前解析——这段代码里没有任何 IP。
     */
    public LbCallView lbCall() {
        LbCallView view = new LbCallView();
        view.setCallSeq(lbCallSeq.incrementAndGet());

        String serviceName = applicationName;
        String url = "http://" + serviceName + SELF_INFO_PATH;
        view.setServiceName(serviceName);
        view.setRequestUrl(url);

        ResponseEntity<ApiResponse<InstanceView>> response = loadBalancedRestTemplate.exchange(
                url, HttpMethod.GET, null, new ParameterizedTypeReference<ApiResponse<InstanceView>>() {
                });
        ApiResponse<InstanceView> body = response.getBody();
        if (body != null) {
            view.setRespondedByServer(body.getData());
            view.setCalleeTraceId(body.getTraceId());
        }
        if (view.getRespondedByServer() != null && view.getRespondedByServer().getPort() > 0) {
            recordHit(String.valueOf(view.getRespondedByServer().getPort()));
        }
        view.setHitsByPort(new LinkedHashMap<String, Integer>(hitsByPort));
        return view;
    }

    /**
     * 单独观察「客户端视角」的负载均衡选择结果。
     * <p>
     * 注意副作用：{@code LoadBalancerClient.choose()} 每调用一次就推进一次轮询位置，
     * 因此这个端点只适合观察，不要和 lb-call 交替调用（会打乱轮询节奏）。
     *
     * @param serviceName 服务名
     * @return 本次被选中的实例；没有可用实例时返回 null
     */
    public InstanceView pickByLoadBalancer(String serviceName) {
        ServiceInstance instance = loadBalancerClient.choose(serviceName);
        return instance == null ? null : InstanceView.from(serviceName, instance);
    }

    private synchronized void recordHit(String port) {
        Integer count = hitsByPort.get(port);
        hitsByPort.put(port, count == null ? 1 : count + 1);
    }

    private String localHost() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException ex) {
            // 某些环境下主机名解析不出来，注册中心里默认也是 127.0.0.1
            return "127.0.0.1";
        }
    }
}
