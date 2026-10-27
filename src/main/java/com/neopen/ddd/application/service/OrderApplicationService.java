package com.neopen.ddd.application.service;

import com.neopen.ddd.application.dto.OrderCreateCommand;
import com.neopen.ddd.domain.model.*;
import com.neopen.ddd.domain.repository.OrderRepository;
import com.neopen.ddd.domain.statemachine.OrderStateMachineManager;
import com.neopen.ddd.domain.statemachine.OrderStateMachineService;
import com.neopen.ddd.infrastructure.integration.inventory.acl.InventoryServiceAcl;
import com.neopen.ddd.infrastructure.integration.payment.acl.PaymentServiceAcl;
import com.neopen.ddd.infrastructure.messaging.KafkaEventPublisher;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.persist.StateMachinePersister;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;

@Service
public class OrderApplicationService extends OrderStateMachineManager {

    private final OrderRepository orderRepository;
    private final InventoryServiceAcl inventoryAcl;
    private final PaymentServiceAcl paymentServiceAcl;
    private final KafkaEventPublisher eventPublisher;
    private final OrderStateMachineService orderStateMachineService;

    public OrderApplicationService(OrderRepository orderRepository,
                                   InventoryServiceAcl inventoryAcl,
                                   KafkaEventPublisher eventPublisher,
                                   StateMachine<OrderStatus, OrderEvent> orderStateMachine,
                                   StateMachinePersister<OrderStatus, OrderEvent, Order> orderStateMachinePersister, PaymentServiceAcl paymentServiceAcl, OrderStateMachineService orderStateMachineService) {
        super(orderStateMachine, orderStateMachinePersister);
        this.orderRepository = orderRepository;
        this.inventoryAcl = inventoryAcl;
        this.eventPublisher = eventPublisher;
        this.paymentServiceAcl = paymentServiceAcl;
        this.orderStateMachineService = orderStateMachineService;
    }

    @Transactional
    public Long createOrder(OrderCreateCommand command) {
        if (!inventoryAcl.reserveStock(command.getProductId(), command.getQuantity())) {
            throw new RuntimeException("库存不足或服务不可用");
        }

        Order order = Order.create(command.getCustomerId(), Arrays.asList(
                ProductItem.create(command.getProductId(), command.getProductName(), command.getQuantity(), new Money(new BigDecimal(command.getPrice()), "CNY")
                )));

        orderRepository.save(order);

        order.getEvents().forEach(eventPublisher::publish);
        order.clearEvents();

        return order.getId();
    }

    @Transactional
    public String payOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("订单不存在"));

        // 检查是否可以支付
        order.pay(null);

        // 调用支付防腐层
        boolean paymentSuccess = paymentServiceAcl.processPayment(order);
        if (!paymentSuccess) {
            throw new RuntimeException("支付失败，请稍后重试");
        }

        // 触发状态机转换
        if (!sendEvent(OrderEvent.PAY, order)) {
            throw new RuntimeException("支付失败, 状态异常，订单号：" + order.getOrderNum());
        }

        orderRepository.save(order);
        order.getEvents().forEach(eventPublisher::publish);
        order.clearEvents();

        return "支付成功，订单号：" + order.getOrderNum();
    }

    @Transactional
    public String shipOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("订单不存在"));
//        order.ship();

        // 触发状态机转换
        if (!sendEvent(OrderEvent.SHIP, order)) {
            throw new RuntimeException("发货失败, 状态异常，订单号：" + order.getOrderNum());
        }

        orderRepository.save(order);
        order.getEvents().forEach(eventPublisher::publish);
        order.clearEvents();

        return "发货成功，订单号：" + order.getOrderNum();
    }

    @Transactional
    public String receiveOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("订单不存在"));

        // 触发状态机转换
        if (!sendEvent(OrderEvent.SIGN, order)) {
            throw new RuntimeException("收货失败, 状态异常，订单号：" + order.getOrderNum());
        }

        orderRepository.save(order);
        order.getEvents().forEach(eventPublisher::publish);
        order.clearEvents();

        return "收货成功，订单号：" + order.getOrderNum();
    }

    @Transactional
    public String cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("订单不存在"));
//        order.cancel();

        // 如果已支付，需要先取消支付
        if (order.getStatus() == OrderStatus.PAID) {
            boolean cancelPaymentSuccess = paymentServiceAcl.cancelPayment(order);
            if (!cancelPaymentSuccess) {
                throw new RuntimeException("取消支付失败");
            }
            order.getOrderPayment().setStatus("CANCELLED");
        }

        // 触发状态机转换
        if (!sendEvent(OrderEvent.CANCEL, order)) {
            throw new RuntimeException("取消失败, 状态异常，订单号：" + order.getOrderNum());
        }

        orderRepository.save(order);
        order.getEvents().forEach(eventPublisher::publish);
        order.clearEvents();

        return "取消成功，订单号：" + order.getOrderNum();
    }


    public String getOrderStatus(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("订单不存在"));

        return order.getStatus().name();
    }
}