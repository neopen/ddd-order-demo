package com.neopen.ddd.domain.event;

import com.neopen.ddd.domain.model.Money;
import com.neopen.ddd.domain.model.OrderEvent;

import java.time.LocalDateTime;

public class OrderPaidEvent implements DomainEvent {
    private Long orderId;
    private String paymentId;
    private Money amount;
    private LocalDateTime occurredAt;

    public OrderPaidEvent(Long orderId, Money amount) {
        this.orderId = orderId;
        this.amount = amount;
        this.occurredAt = LocalDateTime.now();
    }


    @Override
    public String getEventId() {
        return String.format(OrderEvent.PAY.name(), orderId);
    }
}