package com.neopen.ddd.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 订单状态流转历史记录
 */
@Getter
@Entity
@Table(
        name = "order_status_history",
        indexes = {
                @Index(name = "idx_order_history_order_id", columnList = "order_id"),
                @Index(name = "idx_order_history_created_at", columnList = "created_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 订单 ID
     */
    @Column(name = "order_id", nullable = false)
    private Long orderId;

    /**
     * 流转前状态
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 32)
    private OrderStatus fromStatus;

    /**
     * 流转后状态
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 32)
    private OrderStatus toStatus;

    /**
     * 触发事件
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "event", nullable = false, length = 32)
    private OrderEvent event;

    /**
     * 操作人 ID，系统操作时可为空
     */
    @Column(name = "operator_id")
    private Long operatorId;

    /**
     * 操作说明
     */
    @Column(name = "remark", length = 500)
    private String remark;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public OrderStatusHistory(
            Long orderId,
            OrderStatus fromStatus,
            OrderStatus toStatus,
            OrderEvent event,
            Long operatorId,
            String remark) {

        if (orderId == null || toStatus == null || event == null) {
            throw new IllegalArgumentException(
                    "订单ID、目标状态和触发事件不能为空"
            );
        }

        this.orderId = orderId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.event = event;
        this.operatorId = operatorId;
        this.remark = remark;
        this.createdAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }


    public static OrderStatusHistory of(Order order, OrderEvent event, String remark) {
        return new OrderStatusHistory(
                order.getId(),
                order.getPreStatus(),
                order.getStatus(),
                event,
                order.getOperatorId(),
                remark
        );
    }
}