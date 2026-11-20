package com.neopen.ddd.infrastructure.integration.payment;

import com.neopen.ddd.infrastructure.integration.payment.dto.PaymentRequestDTO;
import lombok.extern.slf4j.Slf4j;
import com.neopen.ddd.infrastructure.integration.payment.dto.PaymentResponse;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 支付服务降级工厂：支付服务超时、不可用或熔断 (Circuit Breaker) 时接管调用。
 *
 * <p>分层职责：基础设施层的容错实现，通过 {@code fallbackFactory} 与
 * {@link PaymentClient} 绑定，业务代码无需感知。</p>
 *
 * <p>降级语义：返回 success=false、status=FAILED，表示"支付结果未知或未成功"，
 * 调用方应据此将订单置为待确认或失败，等待对账或人工介入。</p>
 */
@Component
@Slf4j
public class PaymentClientFallbackFactory implements FallbackFactory<PaymentClient> {

    /**
     * 为失败的支付调用创建降级实现。
     *
     * @param cause 触发降级的原始异常，用于日志与失败原因透出
     * @return 始终返回失败结果的 PaymentClient 代理
     */
    @Override
    public PaymentClient create(Throwable cause) {
        log.error("调用支付服务失败", cause);
        return new PaymentClient() {
            /**
             * 支付的降级实现：不确定资金是否已扣，统一按失败上报。
             *
             * @param request 原始支付请求
             * @return success=false 的响应，message 中携带失败原因
             */
            @Override
            public PaymentResponse processPayment(PaymentRequestDTO request) {
                log.error("调用支付服务，支付失败，orderNum: {}, amount: {}, 异常信息: {}",
                        request.getOrderNum(), request.getAmount(), cause.getMessage(), cause);

                // 返回降级处理结果
                return PaymentResponse.builder()
                        .success(false)
                        .message("支付服务暂时不可用: " + cause.getMessage())
                        .status("FAILED")
                        .build();
            }

            /**
             * 撤销支付的降级实现：返回失败结果以便后续补偿任务重试。
             *
             * @param request 原始撤销请求
             * @return success=false 的响应，message 中携带失败原因
             */
            @Override
            public PaymentResponse cancelPayment(PaymentRequestDTO request) {
                log.error("调用支付服务，取消支付失败，orderNum: {}, amount: {}, 异常信息: {}",
                        request.getOrderNum(), request.getAmount(), cause.getMessage(), cause);

                // 返回降级处理结果
                return PaymentResponse.builder()
                        .success(false)
                        .message("支付服务暂时不可用: " + cause.getMessage())
                        .status("FAILED")
                        .build();
            }
        };
    }


}
