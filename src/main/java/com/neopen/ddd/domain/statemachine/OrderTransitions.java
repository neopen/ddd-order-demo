package com.neopen.ddd.domain.statemachine;

import com.neopen.ddd.domain.model.OrderEvent;
import com.neopen.ddd.domain.model.OrderStatus;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

public final class OrderTransitions {

    private static final Map<OrderStatus, Map<OrderEvent, OrderStatus>> TABLE;

    static {
        Map<OrderStatus, Map<OrderEvent, OrderStatus>> t = new EnumMap<>(OrderStatus.class);

        t.put(OrderStatus.CREATED, Map.of(
                OrderEvent.PAY,         OrderStatus.PAID,
                OrderEvent.CANCEL,      OrderStatus.CANCELLED,
                OrderEvent.CLOSE,       OrderStatus.CLOSED
        ));

        t.put(OrderStatus.PAID, Map.of(
                OrderEvent.SHIP,          OrderStatus.SHIPPED,
                OrderEvent.REFUND,  OrderStatus.REFUNDING,
                OrderEvent.CLOSE,         OrderStatus.CLOSED
        ));

        t.put(OrderStatus.SHIPPED, Map.of(
                OrderEvent.SIGN,          OrderStatus.DELIVERED,
                OrderEvent.REFUND,  OrderStatus.REFUNDING
        ));

        t.put(OrderStatus.DELIVERED, Map.of(
                OrderEvent.COMPLETE,       OrderStatus.COMPLETED,
                OrderEvent.REFUND,  OrderStatus.REFUNDING
        ));

        t.put(OrderStatus.REFUNDING, Map.of(
                OrderEvent.REFUND_SUCCESS, OrderStatus.REFUNDED,
                OrderEvent.REFUND_FAILED,  OrderStatus.PAID   // 简化：驳回回原状态，实际需记录原状态
        ));

        // 终态无出边
        TABLE = Collections.unmodifiableMap(t);
    }

    /** 查询迁移目标，非法迁移返回 empty */
    public static Optional<OrderStatus> next(OrderStatus from, OrderEvent event) {
        return Optional.ofNullable(TABLE.getOrDefault(from, Map.of()).get(event));
    }
}