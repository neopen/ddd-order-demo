package com.neopen.ddd.application.dto;

import com.neopen.ddd.domain.model.Order;

import java.time.LocalDateTime;

/**
 * 订单创建结果。
 * <p>作为创建订单接口的响应体，并会被幂等服务序列化后缓存，用于重复请求回放。
 * 字段全部来源于创建后的订单聚合快照。</p>
 *
 * @param orderId        订单主键，保存后生成；重复回放时与首次一致
 * @param orderNum       业务订单号，全局唯一，创建时生成
 * @param status         订单状态名称，创建后为 CREATED
 * @param totalAmount    优惠前订单总额，币种见 {@link MoneyView#currency()}
 * @param discountAmount 命中的优惠金额，无优惠时为 0
 * @param payableAmount  实际应付金额，等于总额减去优惠金额
 * @param discountPolicy 命中的优惠策略编码，无优惠为 NONE
 * @param createdAt      订单创建时间，服务器本地时区
 */
public record OrderCreatedResponse(
        Long orderId,
        String orderNum,
        String status,
        MoneyView totalAmount,
        MoneyView discountAmount,
        MoneyView payableAmount,
        String discountPolicy,
        LocalDateTime createdAt) {

    /**
     * 由订单聚合快照构建创建结果。
     *
     * @param order 已持久化的订单聚合根，非空
     * @return 创建结果视图
     */
    public static OrderCreatedResponse from(Order order) {
        return new OrderCreatedResponse(
                order.getId(),
                order.getOrderNum(),
                order.getStatus().name(),
                MoneyView.from(order.getTotalAmount()),
                MoneyView.from(order.getDiscountAmount()),
                MoneyView.from(order.getPayableAmount()),
                order.getDiscountPolicy(),
                order.getCreatedAt());
    }
}
