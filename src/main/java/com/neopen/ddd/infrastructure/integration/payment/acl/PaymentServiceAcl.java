package com.neopen.ddd.infrastructure.integration.payment.acl;

import com.neopen.ddd.domain.model.Order;
import com.neopen.ddd.infrastructure.integration.payment.PaymentClient;
import com.neopen.ddd.infrastructure.integration.payment.dto.PaymentRequestDTO;
import com.neopen.ddd.infrastructure.integration.payment.dto.PaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 支付防腐层 (Anti-Corruption Layer)。
 *
 * <p>分层职责：把订单聚合与支付服务的 DTO、协议、异常隔离开，向应用层只暴露布尔结果，
 * 业务层不感知 Feign 类型，也不依赖支付服务的字段结构。</p>
 *
 * <p>关键协作对象：{@link PaymentClient} 执行远程调用，
 * {@link PaymentClientFallbackFactory} 在服务不可用时提供降级 (Fallback)。</p>
 *
 * <p>异常转换约定：远程异常与降级结果统一收敛为 false，不向上层抛出，
 * 由调用方决定订单状态流转或触发补偿。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceAcl {

    /** 支付服务远程客户端，由 Spring 通过构造器注入。 */
    private final PaymentClient paymentClient;

    /**
     * 处理支付请求。
     *
     * @param order 待支付订单，提供订单号、客户与应付金额
     * @return true 表示支付成功；false 表示支付失败、结果未知或服务降级
     */
    public boolean processPayment(Order order) {
        try {
            PaymentRequestDTO request = buildPaymentRequest(order);
            PaymentResponse response = paymentClient.processPayment(request);

            // 根据响应结果判断支付是否成功
            return response.isSuccess();

        } catch (Exception e) {
            // 记录异常日志
            log.error("支付服务调用失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 取消支付，用于订单取消或超时后的资金回退。
     *
     * @param order 需撤销支付的订单
     * @return true 表示撤销成功；false 表示失败或服务降级，需后续补偿
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
     * 将订单聚合转换为支付服务请求。
     *
     * <p>客户标识统一加 CUSTOMER- 前缀；支付方式当前固定为 ALIPAY；
     * 金额取订单应付金额并转换为 double 以满足外部协议。</p>
     *
     * @param order 订单聚合
     * @return 支付服务请求 DTO
     */
    private PaymentRequestDTO buildPaymentRequest(Order order) {
        return new PaymentRequestDTO(
                order.getOrderNum(),
                "CUSTOMER-" + order.getCustomerId(),
                order.getPayableAmount().getAmount().doubleValue(),
                order.getPayableAmount().getCurrency(),
                "ALIPAY"
        );
    }
}