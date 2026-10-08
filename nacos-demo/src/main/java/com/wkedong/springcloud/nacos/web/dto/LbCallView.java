package com.wkedong.springcloud.nacos.web.dto;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一次「按服务名调用」的结果快照，用于观察负载均衡到底把请求交给了谁。
 * <p>
 * 只记录「真正返回响应的实例自述」，不记录客户端本地挑中的实例：
 * {@code LoadBalancerClient.choose()} 每调用一次就会消耗一次轮询位置，
 * 在 lb-call 里先 choose 再发请求，等于一次请求消耗两个位置，
 * 双实例场景下会看到「请求总落在同一个端口」的假象。
 * 想看客户端视角的挑选结果，用 {@code GET /nacos/discovery/lb-pick} 单独观察。
 *
 * @author wkedong
 */
public class LbCallView {

    /** 本进程启动以来的第几次 lb-call（观察轮询节奏） */
    private int callSeq;
    private String serviceName;
    /** 调用地址：http://服务名/路径 —— 注意这里没有 IP */
    private String requestUrl;
    /** 本次真正响应请求的实例（由被调方自述，端口即命中端口） */
    private InstanceView respondedByServer;
    /** 被调方的 traceId，便于去对方日志里定位同一次请求 */
    private String calleeTraceId;
    /** 本进程累计命中次数：端口 → 次数，轮询效果的直接证据 */
    private Map<String, Integer> hitsByPort = new LinkedHashMap<String, Integer>();

    public int getCallSeq() {
        return callSeq;
    }

    public void setCallSeq(int callSeq) {
        this.callSeq = callSeq;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getRequestUrl() {
        return requestUrl;
    }

    public void setRequestUrl(String requestUrl) {
        this.requestUrl = requestUrl;
    }

    public InstanceView getRespondedByServer() {
        return respondedByServer;
    }

    public void setRespondedByServer(InstanceView respondedByServer) {
        this.respondedByServer = respondedByServer;
    }

    public String getCalleeTraceId() {
        return calleeTraceId;
    }

    public void setCalleeTraceId(String calleeTraceId) {
        this.calleeTraceId = calleeTraceId;
    }

    public Map<String, Integer> getHitsByPort() {
        return hitsByPort;
    }

    public void setHitsByPort(Map<String, Integer> hitsByPort) {
        this.hitsByPort = hitsByPort;
    }
}
