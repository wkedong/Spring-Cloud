#!/usr/bin/env bash
# =============================================================================
# 升级后核心链路验证（Boot 4.0.8 / Spring Cloud 2025.1.3 / SCA 2025.1.0.0）
# 每个检查都打印「期望 / 实际」，禁止凭印象宣称通过。
# =============================================================================
set -u
PASS=0; FAIL=0
ok()   { PASS=$((PASS+1)); printf '  PASS  %-52s %s\n' "$1" "$2"; }
bad()  { FAIL=$((FAIL+1)); printf '  FAIL  %-52s 期望=%s 实际=%s\n' "$1" "$2" "$3"; }
chk()  { # chk 名称 期望子串 实际值
  case "$3" in *"$2"*) ok "$1" "$(echo "$3" | head -c 110)";; *) bad "$1" "$2" "$(echo "$3" | head -c 110)";; esac
}
code() { curl -s -o /dev/null -w '%{http_code}' -m 20 "$@"; }

echo "############ 1. 注册中心（Eureka 5.0.2）"
APPS=$(curl -s -m 15 http://localhost:6060/eureka/apps -H 'Accept: application/json')
for svc in SERVICE-PRODUCER SERVICE-CONSUMER SERVICE-CONSUMER-FEIGN SERVICE-CONSUMER-RIBBON SERVICE-CONSUMER-RIBBON-HYSTRIX ZUUL; do
  chk "Eureka 已注册 $svc" "$svc" "$APPS"
done
chk "Eureka 页面可访问" "Eureka" "$(curl -s -m 15 http://localhost:6060/ | head -c 200)"

echo "############ 2. 配置中心（Config 5.0.5 + spring.config.import 新接入方式）"
CFG=$(curl -s -m 15 -H 'Accept: application/json' http://localhost:6010/service-producer/dev/develop)
chk "配置中心返回 test-dev-develop" "test-dev-develop" "$CFG"
chk "producer 从配置中心取到配置" "ConfigName is test-dev-develop" "$(curl -s -m 20 http://localhost:6070/testConfig)"

echo "############ 3. producer 直连（含 DB / 连接池）"
chk "producer /testGet" "My port is 6070" "$(curl -s -m 20 http://localhost:6070/testGet)"
chk "producer POST /getAll 走 MySQL 查询" "test-admin" "$(curl -s -m 20 -X POST http://localhost:6070/getAll | head -c 400)"
REFRESH=$(curl -s -m 30 -X POST http://localhost:6070/actuator/refresh)
chk "producer POST /actuator/refresh 可用（Eureka 客户端已非 refresh scope）" "[]" "$REFRESH"

echo "############ 4. 服务间调用（RestTemplate / LoadBalancer）"
chk "consumer(7010) → producer" "My port is 60" "$(curl -s -m 25 http://localhost:7010/testGet)"
chk "ribbon(7030) → producer 负载均衡" "My port is 60" "$(curl -s -m 25 http://localhost:7030/testRibbon)"
INST=$(for i in 1 2 3 4 5 6; do curl -s -m 20 http://localhost:7030/testRibbon; echo; done | grep -oE 'My port is [0-9]+' | sort -u | tr '\n' ' ')
chk "负载均衡命中两个实例（6070/6080）" "My port is 6080" "$INST"
chk "ribbon /lb/instances 实例清单" "6080" "$(curl -s -m 20 http://localhost:7030/lb/instances)"

echo "############ 5. OpenFeign 5.0.3（feign-form 13.6.1）"
chk "feign(7020) → producer" "My port is 60" "$(curl -s -m 25 http://localhost:7020/testFeign)"
chk "feign 透传请求头（b3/自定义）" "x-b3-traceid" "$(curl -s -m 25 http://localhost:7020/testFeignHeaderEcho | tr 'A-Z' 'a-z')"
chk "feign readTimeout 2s 生效（新前缀）" "降级" "$(curl -s -m 25 http://localhost:7020/testFeignTimeout)"

echo "############ 6. CircuitBreaker / Resilience4j（替代 Hystrix）"
chk "hystrix(7040) /testHystrix 超时降级（3s 上限）" "is error" "$(curl -s -m 25 http://localhost:7040/testHystrix)"
chk "TimeLimiter 降级 fallback 生效" "降级" "$(curl -s -m 25 http://localhost:7040/resilience/timelimiter)"
chk "Bulkhead 端点可访问" "bulkhead" "$(curl -s -m 25 http://localhost:7040/resilience/bulkhead | tr 'A-Z' 'a-z')"
chk "Resilience4j 状态端点" "circuitbreaker" "$(curl -s -m 25 http://localhost:7040/resilience/status | tr 'A-Z' 'a-z')"
chk "actuator circuitbreakers 端点" "circuitbreaker" "$(curl -s -m 25 http://localhost:7040/actuator/circuitbreakers | tr 'A-Z' 'a-z')"

echo "############ 7. 网关（Gateway 5：新 starter + spring.cloud.gateway.server.webflux.*）"
chk "网关路由表非空（新前缀生效）" "service-producer" "$(curl -s -m 20 http://localhost:6050/actuator/gateway/routes)"
chk "网关无 token 拦截 401" "401" "$(code http://localhost:6050/api/producer/testGet)"
chk "网关带 token 转发 /api/producer/**" "My port is 60" "$(curl -s -m 25 -H 'X-Token: dev-token' http://localhost:6050/api/producer/testGet)"
chk "网关 StripPrefix 正确" "My port is 60" "$(curl -s -m 25 -H 'X-Token: dev-token' http://localhost:6050/api/producer/testRibbon)"
chk "网关服务发现路由（locator，小写服务名）" "My port is 60" "$(curl -s -m 25 -H 'X-Token: dev-token' http://localhost:6050/service-producer/testGet)"
CODES=$(for i in $(seq 1 14); do code -H 'X-Token: dev-token' http://localhost:6050/api/producer/testGet; echo; done | tr '\n' ' ')
chk "网关限流触发 429（10 次/10s）" "429" "$CODES"
chk "网关 CORS 预检返回 200" "200" "$(code -X OPTIONS -H 'Origin: http://example.com' -H 'Access-Control-Request-Method: GET' http://localhost:6050/api/producer/testGet)"

echo "############ 8. 链路追踪（Micrometer Tracing + Zipkin，替代 Sleuth）"
curl -s -m 20 "http://localhost:6070/testSpan?tag=verify-upgrade" > /dev/null
curl -s -m 25 -H 'X-Token: dev-token' "http://localhost:6050/api/producer/testGet" > /dev/null
curl -s -m 25 http://localhost:7020/testFeign > /dev/null
sleep 6
chk "Zipkin 已收到 service-producer" "service-producer" "$(curl -s -m 20 'http://localhost:9411/api/v2/services')"
SPANS=$(curl -s -m 25 'http://localhost:9411/api/v2/traces?serviceName=service-producer&limit=60&lookback=1800000')
chk "Zipkin 记录到编程式自定义 span" "producer-custom-span" "$SPANS"
chk "Zipkin 记录到 @Observed 注解 span" "producer-annotated-span" "$(curl -s -m 25 'http://localhost:9411/api/v2/spans?serviceName=service-producer&lookback=1800000')"
chk "Zipkin 记录到 Feign 客户端 span" "http get" "$(curl -s -m 25 'http://localhost:9411/api/v2/spans?serviceName=service-consumer-feign&lookback=1800000')"
CROSS=$(python3 - <<'PYEOF'
import json, urllib.request
def get(u): return json.load(urllib.request.urlopen(u, timeout=25))
out=[]
for svc in ['service-consumer','service-consumer-feign','service-consumer-ribbon','service-consumer-ribbon-hystrix']:
    ok='no'
    for t in get(f'http://localhost:9411/api/v2/traces?serviceName={svc}&limit=8&lookback=600000'):
        svcs={s['localEndpoint']['serviceName'] for s in t if s.get('localEndpoint')}
        if 'service-producer' in svcs: ok='yes'; break
    out.append(f'{svc}={ok}')
print(' '.join(out))
PYEOF
)
chk "4 个消费者都有跨服务 trace（同一 traceId）" "service-consumer-ribbon-hystrix=yes" "$CROSS"
chk "RestTemplate 消费者也有跨服务 trace" "service-consumer=yes" "$CROSS"
chk "传播头已注入（W3C traceparent，Boot 4 默认）" "traceparent" "$(curl -s -m 25 http://localhost:7020/testFeignHeaderEcho | tr 'A-Z' 'a-z')"
B3=$(curl -s -m 20 http://localhost:6070/echoHeaders | tr 'A-Z' 'a-z')
chk "producer 暴露 b3 头解析能力" "traceid" "$B3"

echo "############ 9. Actuator / 可观测性（Boot 4：health 包拆到 spring-boot-health）"
chk "producer /actuator/health UP" '"status":"UP"' "$(curl -s -m 20 http://localhost:6070/actuator/health)"
chk "producer /actuator/info" "{}" "$(curl -s -m 20 http://localhost:6070/actuator/info)"
chk "Micrometer → Prometheus 暴露 JVM 指标" "jvm_memory_used_bytes" "$(curl -s -m 20 http://localhost:6070/actuator/prometheus | grep -m1 '^jvm_memory_used_bytes' | cut -d'{' -f1)"
chk "Boot 4 健康检查分组（liveness/readiness）" '"status":"UP"' "$(curl -s -m 20 http://localhost:6070/actuator/health/liveness)"
chk "实例元数据（灰度 version=v1）生效" '"version":"v1"' "$(curl -s -m 20 http://localhost:6070/echoInstance)"

echo "############ 结果"
echo "  PASS=$PASS  FAIL=$FAIL"
[ "$FAIL" -eq 0 ] && echo "  === 核心链路全部通过 ===" || echo "  === 存在失败项，需修复 ==="
