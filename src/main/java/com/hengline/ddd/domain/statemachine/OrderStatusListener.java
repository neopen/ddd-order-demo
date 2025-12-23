package com.hengline.ddd.domain.statemachine;

import com.hengline.ddd.domain.model.Order;
import org.springframework.messaging.Message;
import org.springframework.statemachine.annotation.OnTransition;
import org.springframework.statemachine.annotation.WithStateMachine;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * @description: 状态监听
 */
@Component
@WithStateMachine
@Transactional
public class OrderStatusListener {
    @OnTransition(source = "WAIT_PAYMENT", target = "WAIT_DELIVER")
    public boolean payTransition(Message<OrderEventEnum> message) {
        Order order = (Order) message.getHeaders().get("order");
        assert order != null;
        order.pay();
        System.out.println("支付订单，状态机反馈信息：" + message.getHeaders().toString());
        return true;
    }

    @OnTransition(source = {"WAIT_PAYMENT", "WAIT_DELIVER"}, target = "FINISH")
    public boolean cancelTransition(Message<OrderEventEnum> message) {
        Order order = (Order) message.getHeaders().get("order");
        assert order != null;
        order.cancel();
        System.out.println("取消订单，状态机反馈信息：" + message.getHeaders().toString());
        return true;
    }

    @OnTransition(source = "WAIT_DELIVER", target = "WAIT_RECEIVE")
    public boolean deliverTransition(Message<OrderEventEnum> message) {
        Order order = (Order) message.getHeaders().get("order");
        assert order != null;
        order.ship();
        System.out.println("发货，状态机反馈信息：" + message.getHeaders().toString());
        return true;
    }

    @OnTransition(source = "WAIT_RECEIVE", target = "FINISH")
    public boolean receiveTransition(Message<OrderEventEnum> message) {
        Order order = (Order) message.getHeaders().get("order");
        assert order != null;
        order.complete();
        System.out.println("收货，状态机反馈信息：" + message.getHeaders().toString());
        return true;
    }

}