package com.neopen.ddd.domain.event;

import com.neopen.ddd.domain.model.OrderEvent;
import com.neopen.ddd.domain.model.OrderStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * 订单状态迁移事件。事件仅在数据库事务提交后对外发布。
 * eventId 在事件对象创建时生成，并在该事件的重试/转发过程中保持不变。
 */
public record OrderStatusChangedEvent(
        Long orderId,
        OrderStatus from,
        OrderStatus to,
        OrderEvent event,
        Instant occurredAt,
        String id) implements DomainEvent {

    public OrderStatusChangedEvent(Long orderId, OrderStatus from, OrderStatus to, OrderEvent event) {
        this(orderId, from, to, event, Instant.now(), UUID.randomUUID().toString());
    }

    @Override
    public String getEventId() {
        return id;
    }
}
