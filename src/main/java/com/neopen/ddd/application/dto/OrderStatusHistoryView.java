package com.neopen.ddd.application.dto;

import com.neopen.ddd.domain.model.OrderStatusHistory;

import java.time.LocalDateTime;

/**
 * 状态流转历史视图。
 * <p>读侧输出模型，对应一条状态历史记录，用于还原订单生命周期轨迹。</p>
 *
 * @param id         历史记录主键
 * @param fromStatus 流转前状态名称；创建记录没有前状态，为 null
 * @param toStatus   流转后状态名称
 * @param event      触发本次流转的订单事件名称
 * @param operatorId 操作人标识；系统操作时可为 null
 * @param remark     操作说明，最长 500 字符
 * @param createdAt  记录创建时间，服务器本地时区
 */
public record OrderStatusHistoryView(
        Long id,
        String fromStatus,
        String toStatus,
        String event,
        Long operatorId,
        String remark,
        LocalDateTime createdAt) {

    /**
     * 由状态历史实体构建视图。
     *
     * @param history 状态历史实体，非空
     * @return 状态历史视图
     */
    public static OrderStatusHistoryView from(OrderStatusHistory history) {
        return new OrderStatusHistoryView(
                history.getId(),
                history.getFromStatus() == null ? null : history.getFromStatus().name(),
                history.getToStatus().name(),
                history.getEvent().name(),
                history.getOperatorId(),
                history.getRemark(),
                history.getCreatedAt());
    }
}
