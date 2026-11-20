package com.neopen.ddd.application.dto;

import com.neopen.ddd.domain.model.Order;

import java.time.LocalDateTime;

/**
 * 订单列表视图。
 * <p>读侧输出模型 (Read Model)，仅包含列表展示所需字段，不承载订单项明细，
 * 用于降低列表查询的数据量与序列化开销。</p>
 *
 * @param id            订单主键
 * @param orderNum      业务订单号，全局唯一
 * @param customerId    下单客户标识
 * @param status        订单状态名称，如 CREATED、PAID
 * @param payableAmount 实际应付金额，包含金额与币种
 * @param createdAt     订单创建时间，服务器本地时区
 */
public record OrderSummaryView(
        Long id,
        String orderNum,
        Long customerId,
        String status,
        MoneyView payableAmount,
        LocalDateTime createdAt) {

    /**
     * 由订单聚合快照构建列表摘要视图。
     *
     * @param order 订单聚合根，非空
     * @return 列表摘要视图
     */
    public static OrderSummaryView from(Order order) {
        return new OrderSummaryView(
                order.getId(),
                order.getOrderNum(),
                order.getCustomerId(),
                order.getStatus().name(),
                MoneyView.from(order.getPayableAmount()),
                order.getCreatedAt());
    }
}
