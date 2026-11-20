package com.neopen.ddd.domain.statemachine;

import com.neopen.ddd.domain.model.OrderEvent;
import com.neopen.ddd.domain.model.OrderStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.annotation.OnTransition;
import org.springframework.statemachine.annotation.WithStateMachine;
import org.springframework.stereotype.Component;

/**
 * 订单状态迁移监听器。
 *
 * <p>通过 {@code @WithStateMachine} 订阅状态机迁移回调，只承担日志、指标与审计等观测职责，
 * 不参与业务状态计算，也不访问数据库或调用外部服务。</p>
 *
 * <p>并发：回调在驱动状态机执行的线程内同步触发，日志顺序与实际迁移顺序一致。</p>
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
     *
     * @param context 状态机迁移上下文，包含源状态、目标状态、事件与消息头
     */
    @OnTransition
    public void onTransition(
            StateContext<OrderStatus, OrderEvent> context) {

        // 缺少源状态、目标状态或事件时不是一次完整迁移，直接忽略
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

        // 状态 ID 缺失说明状态机装配异常，仅记录调试日志，不影响主流程
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