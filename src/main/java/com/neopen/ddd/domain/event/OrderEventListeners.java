package com.neopen.ddd.domain.event;

import com.neopen.ddd.domain.model.OrderStatus;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListeners {

    @EventListener
    public void onPaid(OrderStatusChangedEvent e) {
        if (e.to() == OrderStatus.PAID) {
            // 扣减库存锁定、发通知、加积分...
        }
    }

    @EventListener
    public void onShipped(OrderStatusChangedEvent e) {
        if (e.to() == OrderStatus.SHIPPED) {
            // 推送物流、更新履约
        }
    }
}