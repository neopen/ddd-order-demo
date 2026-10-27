package com.neopen.ddd.domain.statemachine;

import com.neopen.ddd.domain.model.OrderEvent;
import lombok.extern.slf4j.Slf4j;
import com.neopen.ddd.domain.model.Order;
import com.neopen.ddd.domain.model.OrderStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.StateMachineContext;
import org.springframework.statemachine.StateMachinePersist;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.statemachine.persist.DefaultStateMachinePersister;
import org.springframework.statemachine.support.DefaultStateMachineContext;

import java.util.EnumSet;

@Configuration
@EnableStateMachineFactory(name = "orderStateMachineFactory")
@Slf4j
public class OrderStateMachineConfig extends EnumStateMachineConfigurerAdapter<OrderStatus, OrderEvent> {

    @Override
    public void configure(StateMachineStateConfigurer<OrderStatus, OrderEvent> states) throws Exception {
        states
                .withStates()
                // 初始状态
                .initial(OrderStatus.CREATED)
                // 终态
                .end(OrderStatus.COMPLETED)
                .end(OrderStatus.CANCELLED)
                .end(OrderStatus.REFUNDED)
                .end(OrderStatus.CLOSED)
                // 注册全部状态
                .states(EnumSet.allOf(OrderStatus.class));
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<OrderStatus, OrderEvent> transitions) throws Exception {
        transitions
                // 1. 支付订单：待支付 -> 已支付
                .withExternal()
                .source(OrderStatus.CREATED)
                .target(OrderStatus.PAID)
                .event(OrderEvent.PAY)

                // 2. 取消订单：待支付 -> 已取消
                .and()
                .withExternal()
                .source(OrderStatus.CREATED)
                .target(OrderStatus.CANCELLED)
                .event(OrderEvent.CANCEL)

                // 3. 取消订单：已支付 -> 已取消
                // 实际业务中通常需要先判断是否允许取消，以及是否需要退款
                .and()
                .withExternal()
                .source(OrderStatus.PAID)
                .target(OrderStatus.CANCELLED)
                .event(OrderEvent.CANCEL)

                // 4. 发货：已支付 -> 已发货
                .and()
                .withExternal()
                .source(OrderStatus.PAID)
                .target(OrderStatus.SHIPPED)
                .event(OrderEvent.SHIP)

                // 5. 签收：已发货 -> 已签收
                .and()
                .withExternal()
                .source(OrderStatus.SHIPPED)
                .target(OrderStatus.DELIVERED)
                .event(OrderEvent.SIGN)

                // 6. 确认完成：已签收 -> 已完成
                .and()
                .withExternal()
                .source(OrderStatus.DELIVERED)
                .target(OrderStatus.COMPLETED)
                .event(OrderEvent.COMPLETE)

                // 7. 申请退款：已支付 -> 退款中
                .and()
                .withExternal()
                .source(OrderStatus.PAID)
                .target(OrderStatus.REFUNDING)
                .event(OrderEvent.REFUND)

                // 8. 申请退款：已发货 -> 退款中
                // 需要结合退货、物流等业务规则判断是否允许
                .and()
                .withExternal()
                .source(OrderStatus.SHIPPED)
                .target(OrderStatus.REFUNDING)
                .event(OrderEvent.REFUND)

                // 9. 申请退款：已签收 -> 退款中
                .and()
                .withExternal()
                .source(OrderStatus.DELIVERED)
                .target(OrderStatus.REFUNDING)
                .event(OrderEvent.REFUND)

                // 10. 退款成功：退款中 -> 已退款
                .and()
                .withExternal()
                .source(OrderStatus.REFUNDING)
                .target(OrderStatus.REFUNDED)
                .event(OrderEvent.REFUND_SUCCESS)

                // 11. 退款失败：退款中 -> 恢复原业务状态
                // 此处以恢复到已支付为例，实际项目建议记录退款前状态
                .and()
                .withExternal()
                .source(OrderStatus.REFUNDING)
                .target(OrderStatus.PAID)
                .event(OrderEvent.REFUND_FAILED)

                // 12. 异常关闭：待支付 -> 异常关闭
                .and()
                .withExternal()
                .source(OrderStatus.CREATED)
                .target(OrderStatus.CLOSED)
                .event(OrderEvent.CLOSE)

                // 13. 异常关闭：已支付 -> 异常关闭
                // 需确认资金处理完成，不能直接丢弃退款义务
                .and()
                .withExternal()
                .source(OrderStatus.PAID)
                .target(OrderStatus.CLOSED)
                .event(OrderEvent.CLOSE);
    }

    /**
     * 状态机持久化配置。
     * 当前示例仅记录日志，并从订单对象读取状态，
     * 并未真正写入 Redis 或数据库。
     */
    @Bean("orderStateMachinePersister")
    public DefaultStateMachinePersister<OrderStatus, OrderEvent, Order> orderStateMachinePersister() {

        return new DefaultStateMachinePersister<>(
                new StateMachinePersist<>() {

                    @Override
                    public void write(
                            StateMachineContext<OrderStatus, OrderEvent> context, Order order) {

                        log.debug("write order status:{}", context.getState());
                    }

                    @Override
                    public StateMachineContext<OrderStatus, OrderEvent> read(Order order) {

                        return new DefaultStateMachineContext<>(
                                order.getStatus(),
                                null,
                                null,
                                null
                        );
                    }
                }
        );
    }
}