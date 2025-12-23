package com.hengline.ddd.domain.event;

import com.hengline.ddd.domain.model.Money;
import com.hengline.ddd.domain.statemachine.OrderEventEnum;

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
        return String.format(OrderEventEnum.PAYED.name(), orderId);
    }
}