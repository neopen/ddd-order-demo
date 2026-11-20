package com.neopen.ddd.infrastructure.readmodel;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * 订单读模型投影 (Order Summary Projection)：面向查询场景的宽表。
 *
 * <p>CQRS 读侧：写侧只关心聚合不变量与领域事件，读侧按展示需求单独建模。
 * 本投影只保存列表页需要的字段，避免查询时联表加载订单项与支付记录。</p>
 *
 * <p>更新方式：由 {@link OrderSummaryProjector} 监听领域事件后更新，
 * 以订单号为业务主键；{@code lastEventAt} 与 {@code lastEventId} 用于抵御
 * 事件乱序和重复投递——旧事件或重复事件到达时直接忽略。</p>
 *
 * <p>一致性：本地监听器在业务事务内同步更新投影，因此单进程内读模型与写模型强一致；
 * 若切换为 Kafka 异步消费，则读模型最终一致，消费端依赖 Inbox 去重。</p>
 */
@Entity
@Table(
        name = "tab_order_summary_view",
        indexes = {
                @Index(name = "idx_order_summary_status", columnList = "status"),
                @Index(name = "idx_order_summary_updated", columnList = "updated_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderSummaryProjection {

    /** 业务主键：订单号，与写侧订单一一对应。 */
    @Id
    @Column(name = "order_num", nullable = false, length = 64)
    private String orderNum;

    /** 订单主键，创建事件不携带，待首次状态变更事件补全。 */
    @Column(name = "order_id")
    private Long orderId;

    /** 下单客户标识，用于按客户维度查询。 */
    @Column(name = "customer_id")
    private Long customerId;

    /** 订单当前状态（冗余自写模型，便于直接按状态过滤）。 */
    @Column(name = "status", nullable = false, length = 32)
    private String status;

    /** 应付金额。 */
    @Column(name = "payable_amount", precision = 12, scale = 2)
    private BigDecimal payableAmount;

    /** 币种。 */
    @Column(name = "currency", length = 8)
    private String currency;

    /** 投影首次创建时间。 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 投影最后更新时间。 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** 最后应用的事件 ID，用于忽略重复消息。 */
    @Column(name = "last_event_id", length = 64)
    private String lastEventId;

    /** 最后应用的事件时间，用于忽略乱序到达的旧消息。 */
    @Column(name = "last_event_at")
    private Instant lastEventAt;

    /** 最后应用的事件类型，便于排查投影来源。 */
    @Column(name = "last_event_type", length = 64)
    private String lastEventType;

    /**
     * 创建空白投影，用于状态事件先于创建事件到达的乱序场景。
     *
     * @param orderNum 订单号
     * @param orderId  订单主键，可为 null
     * @return 待补全的投影
     */
    public static OrderSummaryProjection blank(String orderNum, Long orderId) {
        OrderSummaryProjection projection = new OrderSummaryProjection();
        projection.orderNum = orderNum;
        projection.orderId = orderId;
        projection.status = "UNKNOWN";
        projection.createdAt = LocalDateTime.now();
        projection.updatedAt = projection.createdAt;
        return projection;
    }

    /**
     * 应用订单创建事件。
     *
     * @param customerId 客户标识
     * @param amount     应付金额
     * @param currency   币种
     * @param eventId    事件 ID
     * @param occurredAt 事件时间
     */
    public void applyCreated(Long customerId, BigDecimal amount, String currency,
                             String eventId, Instant occurredAt) {
        if (isStale(occurredAt)) {
            return;
        }
        this.customerId = customerId;
        this.payableAmount = amount;
        this.currency = currency;
        this.status = "CREATED";
        this.lastEventId = eventId;
        this.lastEventAt = occurredAt;
        this.lastEventType = "OrderCreatedEvent";
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 应用订单状态变更事件。
     *
     * @param orderId    订单主键
     * @param newStatus  迁移后的状态
     * @param eventId    事件 ID
     * @param occurredAt 事件时间
     */
    public void applyStatus(Long orderId, String newStatus, String eventId, Instant occurredAt) {
        if (isStale(occurredAt)) {
            return;
        }
        if (orderId != null) {
            this.orderId = orderId;
        }
        this.status = newStatus;
        this.lastEventId = eventId;
        this.lastEventAt = occurredAt;
        this.lastEventType = "OrderStatusChangedEvent";
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 判断事件是否已经过期或重复。
     *
     * <p>规则：事件时间不晚于最后已应用事件时间即忽略。由于同一订单的状态迁移是单向的，
     * 该规则足以过滤重放与乱序；跨越不同降级路径的复杂场景仍需版本号或位点管理。</p>
     *
     * @param occurredAt 事件时间
     * @return true 表示应忽略该事件
     */
    private boolean isStale(Instant occurredAt) {
        return occurredAt != null && lastEventAt != null && !occurredAt.isAfter(lastEventAt);
    }
}
