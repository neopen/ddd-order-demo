package com.neopen.ddd.infrastructure.integration.payment.acl;

import com.neopen.ddd.infrastructure.integration.payment.PaymentClient;
import com.neopen.ddd.infrastructure.integration.payment.dto.PaymentRequestDTO;
import com.neopen.ddd.infrastructure.integration.payment.dto.PaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.neopen.ddd.domain.model.Order;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceAcl {

    private final PaymentClient paymentClient;

    /**
     * 处理支付请求
     */
    public boolean processPayment(Order order) {
        try {
            PaymentRequestDTO request = buildPaymentRequest(order);
            PaymentResponse response = paymentClient.processPayment(request);

            // 根据响应结果判断支付是否成功
            return response.isSuccess();

        } catch (Exception e) {
            // 记录异常日志
            log.error("支付服务调用失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 取消支付
     */
    public boolean cancelPayment(Order order) {
        try {
            PaymentRequestDTO request = buildPaymentRequest(order);
            PaymentResponse response = paymentClient.cancelPayment(request);

            return response.isSuccess();

        } catch (Exception e) {
            log.error("取消支付服务调用失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 构建支付请求对象
     */
    private PaymentRequestDTO buildPaymentRequest(Order order) {
        return new PaymentRequestDTO(
                order.getOrderNum(),
                "CUSTOMER-" + order.getId(), // 模拟客户ID
                order.getTotalAmount().doubleValue(),
                "CNY", // 币种
                "ALIPAY" // 支付方式
        );
    }
}