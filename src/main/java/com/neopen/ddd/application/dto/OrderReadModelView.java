package com.neopen.ddd.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单读模型视图：来自投影表 {@code tab_order_summary_view}，字段按列表展示需求裁剪。
 *
 * @param orderNum      订单号
 * @param orderId       订单主键
 * @param customerId    客户标识
 * @param status        订单状态
 * @param payableAmount 应付金额
 * @param currency      币种
 * @param updatedAt     投影最后更新时间
 * @param lastEventType 最后应用的事件类型
 */
public record OrderReadModelView(
        String orderNum,
        Long orderId,
        Long customerId,
        String status,
        BigDecimal payableAmount,
        String currency,
        LocalDateTime updatedAt,
        String lastEventType) {
}
