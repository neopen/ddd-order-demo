package com.hengline.ddd.domain.event;

public class OrderCreatedEvent implements DomainEvent {
    private static final long serialVersionUID = 1L;
    private Long orderId;

    public OrderCreatedEvent(Long orderId) {
        this.orderId = orderId;
    }

    @Override
    public String getEventId() {
        return String.format("创建订单", orderId);
    }
}