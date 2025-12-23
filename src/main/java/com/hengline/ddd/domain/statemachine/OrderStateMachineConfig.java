package com.hengline.ddd.domain.statemachine;

import lombok.extern.slf4j.Slf4j;
import com.hengline.ddd.domain.model.Order;
import com.hengline.ddd.domain.model.OrderStatusEnum;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.StateMachineContext;
import org.springframework.statemachine.StateMachinePersist;
import org.springframework.statemachine.config.EnableStateMachine;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.statemachine.persist.DefaultStateMachinePersister;
import org.springframework.statemachine.support.DefaultStateMachineContext;

import java.util.EnumSet;

@Configuration
@EnableStateMachine(name = "orderStateMachine")
@Slf4j
public class OrderStateMachineConfig extends EnumStateMachineConfigurerAdapter<OrderStatusEnum, OrderEventEnum> {

    @Override
    public void configure(StateMachineStateConfigurer<OrderStatusEnum, OrderEventEnum> states) throws Exception {
        states
                .withStates()
                .initial(OrderStatusEnum.WAIT_PAYMENT)
                .end(OrderStatusEnum.FINISH)
                .states(EnumSet.allOf(OrderStatusEnum.class));
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<OrderStatusEnum, OrderEventEnum> transitions) throws Exception {
        transitions
//                支付订单
                .withExternal()
                .source(OrderStatusEnum.WAIT_PAYMENT).target(OrderStatusEnum.WAIT_DELIVER).event(OrderEventEnum.PAYED)
//                取消订单
                .and()
                .withExternal()
                .source(OrderStatusEnum.WAIT_PAYMENT).target(OrderStatusEnum.FINISH).event(OrderEventEnum.CANCELLED)
                .and()
                .withExternal()
                .source(OrderStatusEnum.WAIT_DELIVER).target(OrderStatusEnum.FINISH).event(OrderEventEnum.CANCELLED)
//                发货
                .and()
                .withExternal()
                .source(OrderStatusEnum.WAIT_DELIVER).target(OrderStatusEnum.WAIT_RECEIVE).event(OrderEventEnum.DELIVERY)
//                收货
                .and()
                .withExternal()
                .source(OrderStatusEnum.WAIT_RECEIVE).target(OrderStatusEnum.FINISH).event(OrderEventEnum.RECEIVED);
    }

    /**
     * 持久化配置
     * 实际使用中，可以配合redis等，进行持久化操作
     *
     * @return
     */
    @Bean("orderStateMachinePersister")
    public DefaultStateMachinePersister<OrderStatusEnum, OrderEventEnum, Order> orderStateMachinePersister() {
        return new DefaultStateMachinePersister<>(new StateMachinePersist<>() {
            @Override
            public void write(StateMachineContext<OrderStatusEnum, OrderEventEnum> context, Order order) throws Exception {
                //此处并没有进行持久化操作
                log.debug("write order status:{}", context.getState());
            }

            @Override
            public StateMachineContext<OrderStatusEnum, OrderEventEnum> read(Order order) throws Exception {
                //此处直接获取order中的状态，其实并没有进行持久化读取操作
                return new DefaultStateMachineContext<>(order.getStatus(), null, null, null);
            }
        });
    }
}