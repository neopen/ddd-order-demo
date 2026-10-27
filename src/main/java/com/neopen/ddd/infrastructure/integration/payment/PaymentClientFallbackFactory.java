package com.neopen.ddd.infrastructure.integration.payment;

import com.neopen.ddd.infrastructure.integration.payment.dto.PaymentRequestDTO;
import lombok.extern.slf4j.Slf4j;
import com.neopen.ddd.infrastructure.integration.payment.dto.PaymentResponse;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 支付服务降级处理
 */
@Component
@Slf4j
public class PaymentClientFallbackFactory implements FallbackFactory<PaymentClient> {

    @Override
    public PaymentClient create(Throwable cause) {
        log.error("调用支付服务失败", cause);
        return new PaymentClient() {
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
