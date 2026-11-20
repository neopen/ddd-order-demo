package com.neopen.ddd.domain.event;

import com.neopen.ddd.domain.model.OrderStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 领域事件 (Domain Event) 的进程内监听器。
 * <p>作为事件的本地订阅者，只承担日志、通知、缓存刷新等轻量副作用；
 * 跨服务投递统一由 Outbox 中继完成，避免这里与消息发送逻辑重复导致事件被投递两次。</p>
 *
 * <p>事务语义：所有方法都标注 {@code @TransactionalEventListener(AFTER_COMMIT)}，
 * 仅在业务事务成功提交后执行；事务回滚时监听器不会被调用。因此监听器内不能再假设
 * 原事务仍然打开，也不应把本地副作用当作分布式事务的一部分来保证强一致。</p>
 *
 * <p>异常注意：AFTER_COMMIT 阶段抛出的异常无法回滚已提交的业务数据，
 * 因此这里只做可容忍失败的副作用，关键动作应由 Outbox 消费链路重试兜底。</p>
 */
@Component
@Slf4j
public class OrderEventListeners {

    /**
     * 处理订单创建事件：在订单事务提交后记录创建结果。
     * <p>之所以放在提交后，是为了避免“日志已打印但订单随后回滚”的误导信息。</p>
     *
     * @param event 已提交的订单创建事件，携带订单号、客户与应付金额
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("订单创建事件已提交：orderNum={}, customerId={}, payable={}",
                event.orderNum(), event.customerId(), event.payableAmount());
    }

    /**
     * 处理订单状态迁移事件：先记录迁移轨迹，再按目标状态触发对应的本地动作。
     * <p>按 {@code to} 分支是因为同一事件类型可能承载多种迁移，消费方需要根据
     * 目的状态决定后续流程；未列出的状态（如已取消）当前不需要额外副作用。</p>
     *
     * @param event 已提交的状态迁移事件，包含订单主键、前后状态与触发动作
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        log.info("订单状态变更已提交：orderId={}, {} -> {}, event={}",
                event.orderId(), event.from(), event.to(), event.event());

        if (event.to() == OrderStatus.PAID) {
            // 支付完成且事务已提交，此处只做可容忍失败的本地动作：通知、履约准备等。
        } else if (event.to() == OrderStatus.SHIPPED) {
            // 发货已落库，此处适合推送物流单、刷新履约看板等只读型副作用。
        }
    }
}
