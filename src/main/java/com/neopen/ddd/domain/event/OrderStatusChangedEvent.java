package com.neopen.ddd.domain.event;

import com.neopen.ddd.domain.model.OrderEvent;
import com.neopen.ddd.domain.model.OrderStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * 订单状态迁移事件 (Order Status Changed Event)。
 * <p>事件仅在数据库事务提交后对外发布：事务回滚时状态变更未生效，若提前发布会造成
 * 消费端读到不存在的事实，因此所有监听器都必须使用 {@code AFTER_COMMIT} 语义。</p>
 *
 * <p>字段语义：</p>
 * <ul>
 *   <li>{@code orderId}：订单主键，同时作为聚合标识，保证同一订单的事件有序消费。</li>
 *   <li>{@code from}/{@code to}：迁移前后的状态，两者构成状态机 (State Machine) 的一条合法边。</li>
 *   <li>{@code event}：触发本次迁移的领域动作（如支付、发货），便于消费端区分变更原因。</li>
 *   <li>{@code occurredAt}：事件发生时刻，用于排序、延迟度量与审计追溯。</li>
 *   <li>{@code id}：事件唯一 ID，创建时生成，重试/转发期间保持不变，用于幂等去重。</li>
 * </ul>
 */
public record OrderStatusChangedEvent(
        Long orderId,
        OrderStatus from,
        OrderStatus to,
        OrderEvent event,
        Instant occurredAt,
        String id) implements DomainEvent {

    /**
     * 便捷构造器：自动填充事件发生时间与事件 ID。
     * <p>由聚合内部调用，保证每次状态迁移只产生一个稳定的事件 ID，
     * 避免重试时生成新 ID 导致消费端重复处理。</p>
     *
     * @param orderId 订单主键
     * @param from    迁移前状态
     * @param to      迁移后状态
     * @param event   触发迁移的领域动作
     */
    public OrderStatusChangedEvent(Long orderId, OrderStatus from, OrderStatus to, OrderEvent event) {
        this(orderId, from, to, event, Instant.now(), UUID.randomUUID().toString());
    }

    /**
     * @return 事件唯一 ID，供消费端幂等去重
     */
    @Override
    public String getEventId() {
        return id;
    }

    /**
     * @return 聚合类型，固定为 {@code Order}
     */
    @Override
    public String aggregateType() {
        return "Order";
    }

    /**
     * @return 聚合标识，取订单主键的字符串形式以保证同一订单事件有序
     */
    @Override
    public String aggregateId() {
        return String.valueOf(orderId);
    }
}
