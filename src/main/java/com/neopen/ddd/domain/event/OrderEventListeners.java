package com.neopen.ddd.domain.event;

import com.neopen.ddd.domain.model.OrderStatus;
import com.neopen.ddd.infrastructure.messaging.KafkaEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 提交后执行副作用；此处不再修改订单状态。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventListeners {

    private final KafkaEventPublisher kafkaEventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        log.info("订单状态变更已提交：orderId={}, {} -> {}, event={}",
                event.orderId(), event.from(), event.to(), event.event());

        if (event.to() == OrderStatus.PAID) {
            // 可在此触发通知/履约等动作；库存预占与扣减语义需按业务流程区分。
        } else if (event.to() == OrderStatus.SHIPPED) {
            // 可在此推送物流、更新履约。
        }

        // Kafka 发送失败目前只记录日志；生产环境建议用 Outbox 保证 DB 与消息最终一致。
        kafkaEventPublisher.publish(event);
    }
}
