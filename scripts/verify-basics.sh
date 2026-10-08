#!/usr/bin/env bash
# =============================================================================
# springboot-basics 模块冒烟验证（教学文档 docs/10~16 的「动手验证」证据来源）
# 前置：模块已启动在 8010（java -jar springboot-basics/target/springboot-basics-0.0.1-SNAPSHOT.jar）
# 用法：mkdir -p /tmp/sc-upgrade && bash scripts/verify-basics.sh > /tmp/sc-upgrade/basics-smoke.txt 2>&1
# 预期：21 个小节全部符合注释里的预期（鉴权 401、参数校验字段级错误、事务回滚 900/600、
#       自调用陷阱 850/650、缓存命中耗时骤降、自定义健康组件 basics、自定义端点 /actuator/basics 等）
# =============================================================================
set -u
BASE=http://127.0.0.1:8010
TOKEN='X-Token: dev-token'
JSON='Content-Type: application/json'

hr() { echo; echo "=============== $* ==============="; }
req() { echo "--- $* "; }

hr "1. 鉴权：不带 token 访问受保护接口"
req "curl -s -o /dev/null -w '%{http_code}' $BASE/api/users"
curl -s -o /tmp/sc-upgrade/_b1 -w 'HTTP %{http_code}\n' $BASE/api/users; cat /tmp/sc-upgrade/_b1; echo

hr "2. 鉴权：白名单接口免 token"
req "curl -s $BASE/api/public/ping"
curl -s $BASE/api/public/ping; echo

hr "3. 列表接口（带 token）"
req "curl -s -H '$TOKEN' $BASE/api/users"
curl -s -H "$TOKEN" $BASE/api/users; echo

hr "4. 参数校验失败（字段级错误）"
req "POST /api/users 非法数据"
curl -s -X POST -H "$TOKEN" -H "$JSON" \
  -d '{"name":"","email":"bad-email","phone":"12345","age":0}' $BASE/api/users; echo

hr "5. 创建用户成功（含 traceId）"
req "POST /api/users 合法数据"
curl -s -X POST -H "$TOKEN" -H "$JSON" \
  -d '{"name":"王五","email":"wangwu@example.com","phone":"13800138000","age":30}' $BASE/api/users; echo

hr "6. 业务异常（HTTP 400 + 业务码）"
req "curl -s $BASE/api/demo/business-error"
curl -s -w '\nHTTP %{http_code}\n' -H "$TOKEN" $BASE/api/demo/business-error

hr "7. 系统异常（HTTP 500 + 固定文案，堆栈只在日志）"
req "curl -s $BASE/api/demo/system-error"
curl -s -w '\nHTTP %{http_code}\n' -H "$TOKEN" $BASE/api/demo/system-error

hr "8. 配置绑定（@ConfigurationProperties）"
req "curl -s -H '$TOKEN' $BASE/api/config"
curl -s -H "$TOKEN" $BASE/api/config | python3 -m json.tool

hr "9. 配置优先级（属性源顺序 + 自增序列验证）"
req "curl -s -H '$TOKEN' $BASE/api/config/priority"
curl -s -H "$TOKEN" $BASE/api/config/priority | python3 -m json.tool | head -40

hr "10. 缓存：第一次 vs 第二次（耗时对比）"
curl -s -o /dev/null -w '第一次 cost=%{time_total}s\n' -H "$TOKEN" $BASE/api/cache/products/1
curl -s -o /dev/null -w '第二次 cost=%{time_total}s\n' -H "$TOKEN" $BASE/api/cache/products/1
req "curl -s $BASE/api/cache/stats"
curl -s -H "$TOKEN" $BASE/api/cache/stats; echo

hr "11. 缓存自调用陷阱（每次都是慢查询）"
curl -s -o /dev/null -w '第一次 cost=%{time_total}s\n' -H "$TOKEN" $BASE/api/cache/products/1/self-invocation
curl -s -o /dev/null -w '第二次 cost=%{time_total}s\n' -H "$TOKEN" $BASE/api/cache/products/1/self-invocation

hr "12. 事务：初始余额"
curl -s -H "$TOKEN" $BASE/api/tx/accounts; echo

hr "13. 事务：正常转账 100"
curl -s -X POST -H "$TOKEN" "$BASE/api/tx/transfer?fromId=1&toId=2&amount=100" | python3 -m json.tool | head -20
curl -s -H "$TOKEN" $BASE/api/tx/accounts; echo

hr "14. 事务：入账后抛异常 → 回滚（余额应保持 900/600）"
curl -s -w '\nHTTP %{http_code}\n' -X POST -H "$TOKEN" "$BASE/api/tx/transfer-fail?fromId=1&toId=2&amount=100"
curl -s -H "$TOKEN" $BASE/api/tx/accounts; echo

hr "15. 事务：自调用陷阱 → 扣款不回滚（余额应变成 850/650）"
curl -s -X POST -H "$TOKEN" "$BASE/api/tx/self-invocation?fromId=1&toId=2&amount=50" | python3 -m json.tool | head -20
curl -s -H "$TOKEN" $BASE/api/tx/accounts; echo

hr "16. 异步：串行 vs 并行耗时对比"
curl -s -H "$TOKEN" "$BASE/api/async/compare?tasks=5&sleepMillis=300" | python3 -m json.tool

hr "17. Actuator：健康检查（自定义 HealthIndicator）"
curl -s $BASE/actuator/health | python3 -m json.tool

hr "18. Actuator：info（自定义 InfoContributor）"
curl -s $BASE/actuator/info | python3 -m json.tool

hr "19. Actuator：自定义端点 /actuator/basics"
curl -s $BASE/actuator/basics | python3 -m json.tool | head -45

hr "20. Actuator：动态调整日志级别（运行时生效，无需重启）"
req "POST /actuator/loggers/com.wkedong.springboot.basics.web.AuthInterceptor {\"configuredLevel\":\"TRACE\"}"
curl -s -X POST -H "$JSON" -d '{"configuredLevel":"TRACE"}' \
  $BASE/actuator/loggers/com.wkedong.springboot.basics.web.AuthInterceptor; echo
curl -s $BASE/actuator/loggers/com.wkedong.springboot.basics.web.AuthInterceptor | python3 -m json.tool

hr "21. 缓存指标（Micrometer 从 Caffeine 采集）"
curl -s "$BASE/actuator/metrics/cache.gets" | python3 -m json.tool
