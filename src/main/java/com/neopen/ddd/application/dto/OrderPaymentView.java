package com.neopen.ddd.application.dto;

import com.neopen.ddd.domain.model.OrderPayment;

import java.time.LocalDateTime;

/**
 * 支付信息视图。
 * <p>读侧输出模型，来自订单聚合内的支付记录；未支付订单没有该记录。</p>
 *
 * @param paymentId     支付单号，当前由订单号派生
 * @param transactionId 支付渠道交易号，用于对账
 * @param status        渠道支付状态原文，如 COMPLETED
 * @param amount        支付金额，包含金额与币种
 * @param paidAt        支付完成时间，服务器本地时区
 */
public record OrderPaymentView(
        String paymentId,
        String transactionId,
        String status,
        MoneyView amount,
        LocalDateTime paidAt) {

    /**
     * 由支付记录构建视图。
     *
     * @param payment 支付记录，可能为 null（订单尚未支付）
     * @return 支付信息视图；入参为 null 时返回 null，便于响应中省略该字段
     */
    public static OrderPaymentView from(OrderPayment payment) {
        if (payment == null) {
            return null;
        }
        return new OrderPaymentView(
                payment.getPaymentId(),
                payment.getTransactionId(),
                payment.getStatus(),
                MoneyView.from(payment.getAmount()),
                payment.getPaidAt());
    }
}
