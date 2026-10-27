package com.neopen.ddd.domain.statemachine;

import com.neopen.ddd.domain.model.Order;
import com.neopen.ddd.domain.model.OrderEvent;
import com.neopen.ddd.domain.model.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.persist.StateMachinePersister;
import org.springframework.stereotype.Component;

/**
 * <b> 通过代码方式发送状态转换事件
 * <p>
 *
 * @Author neopen
 * @Date 2025/8/28 11:42
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OrderStateMachineManager {

    private final StateMachineFactory<OrderStatus, OrderEvent> stateMachineFactory;
    private final StateMachinePersister<OrderStatus, OrderEvent, Order> persister;

    /**
     * 驱动状态机完成一次事件处理。
     * 返回状态机迁移后的目标状态。
     */
    public OrderStatus sendEvent(Order order, OrderEvent event) {
        String machineId = "order-" + order.getId();

        StateMachine<OrderStatus, OrderEvent> machine =
                stateMachineFactory.getStateMachine(machineId);

        try {
            persister.restore(machine, order);

            // 根据项目使用的 Spring StateMachine 版本，
            // 确认同步或响应式启动方式。
            machine.start();

            Message<OrderEvent> message = MessageBuilder
                    .withPayload(event)
                    .setHeader("order", order)
                    .setHeader("orderId", order.getId())
                    .build();

            boolean accepted = machine.sendEvent(message);

            if (!accepted) {
                throw new IllegalStateException(
                        "状态机拒绝事件: orderId=" + order.getId()
                                + ", event=" + event
                );
            }

            return machine.getState().getId();

        } catch (Exception ex) {
            throw new IllegalStateException(
                    "订单状态机执行失败: orderId=" + order.getId()
                            + ", event=" + event,
                    ex
            );
        } finally {
            try {
                machine.stop();
            } catch (Exception stopError) {
                log.warn("停止状态机失败，orderId={}", order.getId(), stopError);
            }
        }
    }
}
