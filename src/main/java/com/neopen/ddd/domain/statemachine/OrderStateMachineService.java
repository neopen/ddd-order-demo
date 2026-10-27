package com.neopen.ddd.domain.statemachine;

import com.neopen.ddd.domain.event.OrderStatusChangedEvent;
import com.neopen.ddd.domain.model.Order;
import com.neopen.ddd.domain.model.OrderEvent;
import com.neopen.ddd.domain.model.OrderStatus;
import com.neopen.ddd.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * 订单状态迁移用例：状态机负责判定事件是否合法，数据库条件更新负责并发控制。
 * 每次调用获取独立状态机实例，避免多个订单共享同一份运行态。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderStateMachineService {

    private final OrderRepository orderRepo;
    private final OrderStateMachineManager stateMachineManager;
    private final ApplicationEventPublisher publisher;

    /**
     * 触发订单状态迁移
     */
    @Transactional
    public boolean fire(Long orderId, OrderEvent event) {
        if (orderId == null || event == null) {
            throw new IllegalArgumentException("订单ID和事件不能为空");
        }

        Order order = orderRepo.findById(orderId)
                .orElseThrow(() ->
                        new IllegalArgumentException("订单不存在: " + orderId));

        OrderStatus from = order.getStatus();

        // 状态机执行迁移判定
        OrderStatus target = stateMachineManager.sendEvent(order, event);

        // 退款失败必须恢复到退款前状态
        if (event == OrderEvent.REFUND_FAILED) {
            if (from != OrderStatus.REFUNDING
                    || order.getPreStatus() == null) {
                throw new IllegalStateException("退款前状态缺失，无法恢复订单");
            }
            target = order.getPreStatus();
        }

        if (from == target) {
            return false;
        }

        // 聚合根维护领域规则和状态相关字段
        order.transitTo(target);

        // 在当前事务内触发实体更新和乐观锁校验
        orderRepo.saveAndFlush(order);

        // 事务提交后再执行实际的下游处理
        publisher.publishEvent(
                new OrderStatusChangedEvent(orderId, from, target, event)
        );

        log.info("订单状态迁移成功，orderId={}, {} --{}--> {}",
                orderId, from, event, target);

        return true;
    }
}
