package com.neopen.ddd.domain.event;

import com.neopen.ddd.domain.model.OrderEvent;
import com.neopen.ddd.domain.model.OrderStatus;

public record OrderStatusChangedEvent(
        Long orderId,
        OrderStatus from,
        OrderStatus to,
        OrderEvent event) {
}