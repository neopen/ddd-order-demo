package com.neopen.ddd.domain.event;

import com.neopen.ddd.domain.model.OrderEvent;

public class OrderCancelledEvent implements DomainEvent {
    private Long orderId;
    private Long customerId;

    public OrderCancelledEvent(Long orderId, Long customerId) {
        this.orderId = orderId;
        this.customerId = customerId;
    }

    @Override
    public String getEventId() {
        return String.format(OrderEvent.CANCEL.name(), orderId);
    }
}