package com.neopen.ddd.domain.statemachine;

import com.neopen.ddd.domain.event.OrderStatusChangedEvent;
import com.neopen.ddd.domain.model.Order;
import com.neopen.ddd.domain.model.OrderEvent;
import com.neopen.ddd.domain.model.OrderStatus;
import com.neopen.ddd.domain.model.OrderStatusHistory;
import com.neopen.ddd.domain.repository.OrderRepository;
import com.neopen.ddd.domain.repository.OrderStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 订单状态迁移应用服务。
 * 负责事务、聚合根变更、持久化和领域事件发布；状态机生命周期由 Manager 负责。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderStateMachineService {

    private final OrderRepository orderRepo;
    private final OrderStatusHistoryRepository historyRepository;
    private final ApplicationEventPublisher publisher;
    private final OrderStateMachineManager stateMachineManager;

    /**
     * @return true 表示订单状态已迁移；非法事件、状态冲突或并发冲突会抛出异常。
     */
    @Transactional
    public boolean fire(Long orderId, OrderEvent event) {
        if (orderId == null || event == null) {
            throw new IllegalArgumentException("订单ID和订单事件不能为空");
        }

        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在: " + orderId));
        OrderStatus from = order.getStatus();

        // Manager 只负责状态机执行；目标状态由状态机配置（包括退款失败的 Guard）决定。
        OrderStatus target = stateMachineManager.sendEvent(order, event);
        if (target == from) {
            throw new IllegalStateException(
                    "状态机接受了事件但订单状态未变化: orderId=" + orderId + ", event=" + event);
        }

        // 聚合根负责维护 preStatus、updatedAt 等领域不变量。
        order.transitTo(target);

        // 触发 JPA @Version 乐观锁检查；失败时事务回滚。
        orderRepo.saveAndFlush(order);

//        状态记录
        historyRepository.save(OrderStatusHistory.of(order, event, "订单状态流转"));

        // 监听器应使用 @TransactionalEventListener(AFTER_COMMIT)，避免提交前执行副作用。
        publisher.publishEvent(new OrderStatusChangedEvent(orderId, from, target, event));
        log.info("订单状态迁移成功，orderId={}, {} --{}--> {}", orderId, from, event, target);
        return true;
    }

}
