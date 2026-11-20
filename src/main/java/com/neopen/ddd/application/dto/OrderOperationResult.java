package com.neopen.ddd.application.dto;

import com.neopen.ddd.domain.model.Order;

/**
 * 订单操作结果。
 * <p>支付、发货、取消等命令接口的统一响应体，也会作为幂等缓存内容被序列化。
 * 字段取自操作完成后的订单最新快照。</p>
 *
 * @param orderId  订单主键
 * @param orderNum 业务订单号，全局唯一
 * @param status   操作完成后的订单状态名称
 * @param message  面向调用方的中文提示，说明本次操作结果
 */
public record OrderOperationResult(
        Long orderId,
        String orderNum,
        String status,
        String message) {

    /**
     * 由订单聚合快照构建操作结果。
     *
     * @param order   操作后的订单聚合根，非空
     * @param message 返回给调用方的提示信息
     * @return 操作结果视图
     */
    public static OrderOperationResult of(Order order, String message) {
        return new OrderOperationResult(
                order.getId(),
                order.getOrderNum(),
                order.getStatus().name(),
                message);
    }
}
