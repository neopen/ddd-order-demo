package com.neopen.ddd.mocks.payment;

import com.neopen.ddd.infrastructure.integration.payment.dto.PaymentRequestDTO;
import com.neopen.ddd.infrastructure.integration.payment.dto.PaymentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存支付服务 (In-Memory Payment Service)：模拟运行在端口 9002 的第三方支付渠道，
 * 提供支付、撤销与查询能力，供订单服务的支付防腐层 (ACL) 通过 HTTP 调用。
 * <p>幂等 (Idempotency) 实现：以订单号为键缓存首次支付回执，
 * 借助 {@link ConcurrentHashMap#computeIfAbsent} 的原子性保证并发重复请求只生成一笔交易，
 * 后到的请求直接返回首次结果，不会重复扣款。</p>
 * <p>数据结构与线程安全：回执保存在并发映射中，首次写入是原子操作，读取无锁；
 * 支付记录仅存在于进程内存，重启后丢失，因此只用于联调与教学演示。</p>
 * <p>注意：撤销只移除内存记录，不做真实资金冲正，也不校验订单状态。</p>
 */
@RestController
@RequestMapping("/api/payments")
@Slf4j
public class PaymentMockController {

    /** 支付回执表：键为订单号，值为首次支付结果，用于幂等回放与查询。 */
    private final Map<String, PaymentResponse> payments = new ConcurrentHashMap<>();

    /**
     * 处理支付请求：按订单号幂等 (Idempotency)，同一订单号只成功处理一次。
     * <p>首次请求生成交易号并写入回执；重复请求即使金额不同也直接返回首次结果，
     * 以此模拟支付渠道的幂等能力。参数非法时返回 REJECTED，而不抛异常。</p>
     *
     * @param request 支付请求，包含订单号、金额与币种
     * @return 支付回执，成功时包含支付单号、交易号与状态
     */
    @PostMapping("/process")
    public PaymentResponse process(@RequestBody PaymentRequestDTO request) {
        // 校验：订单号为空或金额非正时直接拒绝，避免产生无效交易
        if (request == null || request.getOrderNum() == null
                || request.getOrderNum().isBlank() || request.getAmount() <= 0) {
            return PaymentResponse.builder()
                    .success(false)
                    .status("REJECTED")
                    .message("订单号或金额非法")
                    .build();
        }

        // 执行：原子完成“已有回执则回放、没有则生成首笔交易”
        return payments.computeIfAbsent(request.getOrderNum(), orderNum -> {
            String transactionId = "TXN-" + UUID.randomUUID()
                    .toString().replace("-", "").substring(0, 16);
            log.info("支付成功: orderNum={}, amount={} {}, transactionId={}",
                    orderNum, request.getAmount(), request.getCurrency(), transactionId);
            return PaymentResponse.builder()
                    .success(true)
                    .paymentId("PAY-" + orderNum)
                    .transactionId(transactionId)
                    .status("COMPLETED")
                    .message("支付成功")
                    .build();
        });
    }

    /**
     * 撤销支付：移除该订单号的支付回执并返回 CANCELLED。
     * <p>该方法幂等：无论是否存在支付记录都返回成功，便于调用方安全重试；
     * 撤销后同一订单号可再次发起支付，与新交易无异（演示场景的简化处理）。</p>
     *
     * @param request 撤销请求，至少包含订单号
     * @return 撤销结果，成功时状态为 CANCELLED
     */
    @PostMapping("/cancel")
    public PaymentResponse cancel(@RequestBody PaymentRequestDTO request) {
        // 校验：缺少订单号无法定位记录，直接拒绝
        if (request == null || request.getOrderNum() == null) {
            return PaymentResponse.builder()
                    .success(false)
                    .status("REJECTED")
                    .message("订单号不能为空")
                    .build();
        }

        // 执行：移除回执即代表资金侧已撤销，演示环境不做真实冲正
        payments.remove(request.getOrderNum());
        log.info("支付已撤销: orderNum={}", request.getOrderNum());
        return PaymentResponse.builder()
                .success(true)
                .paymentId("PAY-" + request.getOrderNum())
                .status("CANCELLED")
                .message("支付已撤销")
                .build();
    }

    /**
     * 按订单号查询支付回执。
     * <p>未支付、已撤销或服务重启后都会查不到记录，统一返回 NOT_FOUND，
     * 由调用方自行区分业务含义。</p>
     *
     * @param orderNum 订单号
     * @return 支付回执；无记录时返回成功标记为 false 的 NOT_FOUND 结果
     */
    @GetMapping("/{orderNum}")
    public PaymentResponse query(@PathVariable("orderNum") String orderNum) {
        PaymentResponse response = payments.get(orderNum);
        if (response == null) {
            return PaymentResponse.builder()
                    .success(false)
                    .status("NOT_FOUND")
                    .message("未找到支付记录")
                    .build();
        }
        return response;
    }
}
