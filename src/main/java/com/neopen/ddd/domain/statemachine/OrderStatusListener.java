package com.neopen.ddd.domain.statemachine;

import com.neopen.ddd.domain.model.Order;
import com.neopen.ddd.domain.model.OrderEvent;
import com.neopen.ddd.domain.model.OrderPayment;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class OrderStatusListener {
    private static final String ORDER_HEADER = "order";

    /**
     * 支付成功：CREATED -> PAID
     */
    @OnTransition(source = "CREATED", target = "PAID")
    public boolean payTransition(Message<OrderEvent> message) {
        Order order = getOrder(message);
        if (order == null) {
            return false;
        }

        // 设置支付信息
        OrderPayment paymentInfo = new OrderPayment(
                "PAY-" + order.getId(),
                "TXN-" + order.getId(),
                "COMPLETED", order
        );

        order.pay(paymentInfo);

        logTransition("支付订单", message, order);
        return true;
    }

    /**
     * 取消订单：CREATED -> CANCELLED
     */
    @OnTransition(source = "CREATED", target = "CANCELLED")
    public boolean cancelCreatedTransition(Message<OrderEvent> message) {
        Order order = getOrder(message);
        if (order == null) {
            return false;
        }

        order.cancel();
        logTransition("取消待支付订单", message, order);
        return true;
    }

    /**
     * 取消订单：PAID -> CANCELLED
     * <p>
     * 注意：已支付订单取消通常需要先处理退款。
     */
    @OnTransition(source = "PAID", target = "CANCELLED")
    public boolean cancelPaidTransition(Message<OrderEvent> message) {
        Order order = getOrder(message);
        if (order == null) {
            return false;
        }

        order.cancel();
        logTransition("取消已支付订单", message, order);
        return true;
    }

    /**
     * 发货：PAID -> SHIPPED
     */
    @OnTransition(source = "PAID", target = "SHIPPED")
    public boolean shipTransition(Message<OrderEvent> message) {
        Order order = getOrder(message);
        if (order == null) {
            return false;
        }

        order.ship();
        logTransition("订单发货", message, order);
        return true;
    }

    /**
     * 确认签收：SHIPPED -> DELIVERED
     */
    @OnTransition(source = "SHIPPED", target = "DELIVERED")
    public boolean signTransition(Message<OrderEvent> message) {
        Order order = getOrder(message);
        if (order == null) {
            return false;
        }

        order.sign();
        logTransition("订单签收", message, order);
        return true;
    }

    /**
     * 订单完成：DELIVERED -> COMPLETED
     */
    @OnTransition(source = "DELIVERED", target = "COMPLETED")
    public boolean completeTransition(Message<OrderEvent> message) {
        Order order = getOrder(message);
        if (order == null) {
            return false;
        }

        order.complete();
        logTransition("订单完成", message, order);
        return true;
    }

    /**
     * 申请退款：PAID -> REFUNDING
     */
    @OnTransition(source = "PAID", target = "REFUNDING")
    public boolean refundTransition(Message<OrderEvent> message) {
        Order order = getOrder(message);
        if (order == null) {
            return false;
        }

        // 执行退款申请业务，实际退款应结合支付渠道和幂等机制处理
        order.startRefund();
        logTransition("申请退款", message, order);
        return true;
    }

    /**
     * 退款成功：REFUNDING -> REFUNDED
     */
    @OnTransition(source = "REFUNDING", target = "REFUNDED")
    public boolean refundSuccessTransition(Message<OrderEvent> message) {
        Order order = getOrder(message);
        if (order == null) {
            return false;
        }

        order.markRefunded();
        logTransition("退款成功", message, order);
        return true;
    }

    /**
     * 提取订单对象。
     */
    private Order getOrder(Message<OrderEvent> message) {
        if (message == null) {
            log.error("状态机消息为空");
            return null;
        }

        Object value = message.getHeaders().get(ORDER_HEADER);
        if (!(value instanceof Order)) {
            log.error("状态机消息缺少有效的订单对象，event={}",
                    message.getPayload());
            return null;
        }

        return (Order) value;
    }

    /**
     * 统一记录状态流转日志。
     */
    private void logTransition(
            String action,
            Message<OrderEvent> message,
            Order order) {

        log.info("{}，orderId={}，event={}，headers={}",
                action,
                order.getId(),
                message.getPayload(),
                message.getHeaders());
    }

}