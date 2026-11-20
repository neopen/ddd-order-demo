package com.neopen.ddd.application.dto;

import com.neopen.ddd.domain.model.Order;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单详情视图。
 * <p>读侧输出模型，汇总订单主体、金额、订单项与支付信息，供详情页一次性获取。
 * 字段均为只读快照，不暴露聚合的变更行为。</p>
 *
 * @param id             订单主键
 * @param orderNum       业务订单号，全局唯一
 * @param customerId     下单客户标识
 * @param status         当前订单状态名称
 * @param preStatus      退款前状态名称；非退款流程中为 null
 * @param totalAmount    优惠前订单总额
 * @param discountAmount 优惠金额，无优惠时为 0
 * @param payableAmount  实际应付金额，等于总额减去优惠金额
 * @param discountPolicy 命中的优惠策略编码，无优惠为 NONE
 * @param stockReserved  是否已成功预占库存，用于判断取消时是否需要释放
 * @param createdAt      创建时间，服务器本地时区
 * @param updatedAt      最后更新时间，服务器本地时区
 * @param items          订单项视图列表，按聚合内顺序输出
 * @param payment        支付信息视图；未支付时为 null
 */
public record OrderDetailView(
        Long id,
        String orderNum,
        Long customerId,
        String status,
        String preStatus,
        MoneyView totalAmount,
        MoneyView discountAmount,
        MoneyView payableAmount,
        String discountPolicy,
        boolean stockReserved,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<OrderItemView> items,
        OrderPaymentView payment) {

    /**
     * 由订单聚合快照构建详情视图。
     *
     * @param order 订单聚合根，非空
     * @return 订单详情视图
     */
    public static OrderDetailView from(Order order) {
        return new OrderDetailView(
                order.getId(),
                order.getOrderNum(),
                order.getCustomerId(),
                order.getStatus().name(),
                order.getPreStatus() == null ? null : order.getPreStatus().name(),
                MoneyView.from(order.getTotalAmount()),
                MoneyView.from(order.getDiscountAmount()),
                MoneyView.from(order.getPayableAmount()),
                order.getDiscountPolicy(),
                order.isStockReserved(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getProductItems().stream().map(OrderItemView::from).toList(),
                OrderPaymentView.from(order.getOrderPayment()));
    }
}
