package com.neopen.ddd.domain.statemachine;

import com.neopen.ddd.domain.model.Order;
import com.neopen.ddd.domain.model.OrderEvent;
import com.neopen.ddd.domain.model.OrderStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.persist.StateMachinePersister;
import org.springframework.stereotype.Component;

/**
 * 状态机基础设施管理器。
 *
 * <p>负责状态机实例的获取、状态恢复、事件发送和生命周期管理；
 * 不负责订单聚合持久化，也不发布领域事件。</p>
 */
@Component
@Slf4j
public class OrderStateMachineManager {

    private static final String ORDER_HEADER = "order";
    private static final String ORDER_ID_HEADER = "orderId";

    private final StateMachineFactory<OrderStatus, OrderEvent> stateMachineFactory;
    private final StateMachinePersister<OrderStatus, OrderEvent, Order> persister;

    public OrderStateMachineManager(
            @Qualifier("orderStateMachineFactory")
            StateMachineFactory<OrderStatus, OrderEvent> stateMachineFactory,
            @Qualifier("orderStateMachinePersister")
            StateMachinePersister<OrderStatus, OrderEvent, Order> persister) {
        this.stateMachineFactory = stateMachineFactory;
        this.persister = persister;
    }

    /**
     * 驱动状态机处理一次订单事件，并返回迁移后的目标状态。
     * 订单状态的实际持久化由应用服务和 JPA 事务负责。
     */
    public OrderStatus sendEvent(Order order, OrderEvent event) {
        if (order == null || order.getId() == null || event == null) {
            throw new IllegalArgumentException("订单、已持久化的订单ID和事件不能为空");
        }

        StateMachine<OrderStatus, OrderEvent> machine =
                stateMachineFactory.getStateMachine("order-" + order.getId());

        // 工厂可能按 machineId 返回同一个实例；同一 JVM 内对该实例串行执行。
        synchronized (machine) {
            try {
                // 1. 先启动状态机
                machine.start();

                // 2. 再恢复订单在数据库中的实际状态
                persister.restore(machine, order);

                // 3. 使用当前订单状态处理事件
                Message<OrderEvent> message = MessageBuilder
                        .withPayload(event)
                        .setHeader(ORDER_HEADER, order)
                        .setHeader(ORDER_ID_HEADER, order.getId())
                        .build();

                boolean accepted = machine.sendEvent(message);

                if (!accepted) {
                    throw new IllegalStateException(
                            "状态机拒绝订单事件: orderId=" + order.getId()
                                    + ", currentStatus=" + order.getStatus()
                                    + ", event=" + event);
                }

                if (machine.getState() == null
                        || machine.getState().getId() == null) {
                    throw new IllegalStateException(
                            "状态机处理后未能获取目标状态，orderId=" + order.getId());
                }

                return machine.getState().getId();

            } catch (Exception ex) {
                if (ex instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }

                throw new IllegalStateException(
                        "订单状态机执行失败: orderId=" + order.getId()
                                + ", event=" + event,
                        ex);
            } finally {
                try {
                    machine.stop();
                } catch (Exception stopError) {
                    log.warn("停止订单状态机失败，orderId={}",
                            order.getId(), stopError);
                }
            }
        }
    }
}
