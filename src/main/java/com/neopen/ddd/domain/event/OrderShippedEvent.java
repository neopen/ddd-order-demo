package com.neopen.ddd.domain.event;

import com.neopen.ddd.domain.model.OrderEvent;

public class OrderShippedEvent implements DomainEvent {
    private Long orderId;

    public OrderShippedEvent(Long orderId) {
        this.orderId = orderId;
    }


    @Override
    public String getEventId() {
        return String.format(OrderEvent.SHIP.name(), orderId);
    }
}