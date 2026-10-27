package com.neopen.ddd.domain.event;

import com.neopen.ddd.domain.model.OrderStatus;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListeners {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaid(OrderStatusChangedEvent e) {
        if (e.to() == OrderStatus.PAID) {
            // 扣减库存锁定、发通知、加积分...
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onShipped(OrderStatusChangedEvent e) {
        if (e.to() == OrderStatus.SHIPPED) {
            // 推送物流、更新履约
        }
    }
}