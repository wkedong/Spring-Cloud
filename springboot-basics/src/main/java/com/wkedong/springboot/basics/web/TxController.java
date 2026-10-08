package com.wkedong.springboot.basics.web;

import com.wkedong.springboot.basics.service.TransferService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 事务演示接口。
 * <pre>
 * GET  /api/tx/accounts                                  查看账户余额
 * POST /api/tx/transfer?fromId=1&toId=2&amount=100       正常转账（两边同时变化）
 * POST /api/tx/transfer-fail?fromId=1&toId=2&amount=100  入账后抛异常 → 验证回滚（余额不变）
 * POST /api/tx/self-invocation?fromId=1&toId=2&amount=50 自调用陷阱 → 扣款不回滚
 * </pre>
 * 建议按「转账 → 转账失败 → 自调用」的顺序逐个执行并对比余额。
 *
 * @author wkedong
 */
@RestController
@RequestMapping("/api/tx")
public class TxController {

    private final TransferService transferService;

    public TxController(TransferService transferService) {
        this.transferService = transferService;
    }

    @GetMapping("/accounts")
    public ApiResponse<Map<String, Object>> accounts() {
        return ApiResponse.ok(transferService.allAccounts());
    }

    @PostMapping("/transfer")
    public ApiResponse<Map<String, Object>> transfer(@RequestParam("fromId") Long fromId,
                                                     @RequestParam("toId") Long toId,
                                                     @RequestParam("amount") BigDecimal amount) {
        return ApiResponse.ok(transferService.transfer(fromId, toId, amount));
    }

    /** 预期抛业务异常（HTTP 400 + 业务码），并验证余额已回滚 */
    @PostMapping("/transfer-fail")
    public ApiResponse<Map<String, Object>> transferFail(@RequestParam("fromId") Long fromId,
                                                         @RequestParam("toId") Long toId,
                                                         @RequestParam("amount") BigDecimal amount) {
        return ApiResponse.ok(transferService.transferWithFailure(fromId, toId, amount));
    }

    /** 预期「没有报错」但余额已被错误地扣掉一半——事务失效现场 */
    @PostMapping("/self-invocation")
    public ApiResponse<Map<String, Object>> selfInvocation(@RequestParam("fromId") Long fromId,
                                                           @RequestParam("toId") Long toId,
                                                           @RequestParam("amount") BigDecimal amount) {
        return ApiResponse.ok(transferService.transferSelfInvocationBroken(fromId, toId, amount));
    }
}
