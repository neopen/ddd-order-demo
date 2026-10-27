package com.neopen.ddd.domain.statemachine;

import com.neopen.ddd.domain.model.OrderEvent;
import com.neopen.ddd.domain.model.OrderStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.annotation.OnTransition;
import org.springframework.statemachine.annotation.WithStateMachine;
import org.springframework.stereotype.Component;

/**
 * 状态监听
 */
@Slf4j
@Component
@WithStateMachine
public class OrderStatusListener {

    /**
     * 监听状态机的状态迁移。
     * <p>
     * 只负责日志、指标、审计等观测工作，
     * 不修改 Order，不保存数据库，不调用外部支付接口。
     */
    @OnTransition
    public void onTransition(
            StateContext<OrderStatus, OrderEvent> context) {

        if (context == null
                || context.getSource() == null
                || context.getTarget() == null
                || context.getEvent() == null) {
            log.debug("忽略非完整的状态迁移回调");
            return;
        }

        OrderStatus source = context.getSource().getId();
        OrderStatus target = context.getTarget().getId();
        OrderEvent event = context.getEvent();

        if (source == null || target == null) {
            log.debug("忽略缺少状态标识的迁移回调");
            return;
        }

        Object orderId = context.getMessageHeader("orderId");

        log.info(
                "状态机迁移：orderId={}, {} --{}--> {}",
                orderId, source, event, target
        );
    }
}