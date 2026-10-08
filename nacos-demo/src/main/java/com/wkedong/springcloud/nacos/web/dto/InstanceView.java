package com.wkedong.springcloud.nacos.web.dto;

import org.springframework.cloud.client.ServiceInstance;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一个服务实例的「可读视图」。
 * <p>
 * 从 {@link ServiceInstance} 转换而来，字段与 Nacos 控制台里的实例行一一对应，
 * 便于把 curl 输出和控制台截图对上号。
 *
 * @author wkedong
 */
public class InstanceView {

    private String serviceName;
    private String host;
    private int port;
    private String instanceId;
    /** http://host:port，方便直接复制去 curl */
    private String uri;
    /** Nacos 实例元数据（bootstrap.yml 的 discovery.metadata 会原样带过来） */
    private Map<String, String> metadata = new LinkedHashMap<String, String>();
    /** 是不是「当前这个进程」——多实例演示时用来确认请求打到谁 */
    private boolean local;

    /**
     * 由注册中心返回的实例对象转换。
     *
     * @param serviceName 服务名
     * @param instance    注册中心给出的实例
     * @return 视图对象
     */
    public static InstanceView from(String serviceName, ServiceInstance instance) {
        InstanceView view = new InstanceView();
        view.serviceName = serviceName != null ? serviceName : instance.getServiceId();
        view.host = instance.getHost();
        view.port = instance.getPort();
        view.instanceId = instance.getInstanceId();
        view.uri = "http://" + instance.getHost() + ":" + instance.getPort();
        if (instance.getMetadata() != null) {
            view.metadata.putAll(instance.getMetadata());
        }
        return view;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public boolean isLocal() {
        return local;
    }

    public void setLocal(boolean local) {
        this.local = local;
    }
}
