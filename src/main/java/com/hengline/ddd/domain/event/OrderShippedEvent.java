package com.hengline.ddd.domain.event;

import com.hengline.ddd.domain.statemachine.OrderEventEnum;

public class OrderShippedEvent implements DomainEvent {
    private Long orderId;

    public OrderShippedEvent(Long orderId) {
        this.orderId = orderId;
    }


    @Override
    public String getEventId() {
        return String.format(OrderEventEnum.DELIVERY.name(), orderId);
    }
}