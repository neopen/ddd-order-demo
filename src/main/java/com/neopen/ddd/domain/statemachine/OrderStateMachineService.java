package com.neopen.ddd.domain.statemachine;

import com.neopen.ddd.domain.event.OrderStatusChangedEvent;
import com.neopen.ddd.domain.model.Order;
import com.neopen.ddd.domain.model.OrderEvent;
import com.neopen.ddd.domain.model.OrderStatus;
import com.neopen.ddd.domain.repository.OrderRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ConcurrentModificationException;

/**
 * 状态机服务
 */
@Service
public class OrderStateMachineService {

    private final OrderRepository orderRepo;
    private final ApplicationEventPublisher publisher;

    public OrderStateMachineService(OrderRepository orderRepo,
                                    ApplicationEventPublisher publisher) {
        this.orderRepo = orderRepo;
        this.publisher = publisher;
    }

    /**
     * 触发一次状态迁移。
     *
     * @return true 表示迁移成功；false 表示幂等命中（已是目标状态或重复事件）
     */
    @Transactional
    public boolean fire(Long orderId, OrderEvent event) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在: " + orderId));

        OrderStatus from = order.getStatus();
        OrderStatus target = OrderTransitions.next(from, event)
                .orElseThrow(() -> new IllegalStateException(
                        String.format("非法迁移: %s --%s--> ?", from, event)));

        // 特殊处理：退款驳回回滚到退款前状态
        if (event == OrderEvent.REFUND_FAILED) {
            target = order.getPreStatus() != null
                    ? order.getPreStatus() : OrderStatus.PAID;
        }

        // 幂等：已处于目标状态，直接返回
        if (from == target) {
            return false;
        }

        // 乐观锁：UPDATE orders SET status=? , version=version+1
        // WHERE id=? AND status=? AND version=?
        int updated = orderRepo.updateStatusWithVersion(
                orderId, target.name(), from.name(), order.getVersion(),
                target == OrderStatus.REFUNDING ? from.name() : null);

        if (updated == 0) {
            // 并发冲突或状态已变，重试或抛异常由上层决定
            throw new ConcurrentModificationException("订单状态并发更新失败: " + orderId);
        }

        // 迁移成功后发布领域事件，下游异步消费
        publisher.publishEvent(new OrderStatusChangedEvent(
                orderId, from, target, event));

        return true;
    }
}