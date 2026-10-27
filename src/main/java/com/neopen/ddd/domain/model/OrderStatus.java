package com.neopen.ddd.domain.model;

/**
 * 订单状态枚举
 * 定义了订单在其生命周期中可能的所有状态
 */
public enum OrderStatus {
    CREATED,      // 待支付
    PAID,         // 已支付
    SHIPPED,      // 已发货
    DELIVERED,    // 已签收
    COMPLETED,    // 已完成（终态）
    CANCELLED,    // 已取消（终态）
    REFUNDING,    // 退款中
    REFUNDED,     // 已退款（终态）
    CLOSED        // 异常关闭（终态）
}
