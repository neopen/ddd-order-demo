package com.neopen.ddd.application.service;

import com.neopen.ddd.application.dto.OrderCreateCommand;
import com.neopen.ddd.domain.model.*;
import com.neopen.ddd.domain.repository.OrderRepository;
import com.neopen.ddd.domain.statemachine.OrderStateMachineService;
import com.neopen.ddd.infrastructure.integration.inventory.acl.InventoryServiceAcl;
import com.neopen.ddd.infrastructure.integration.payment.acl.PaymentServiceAcl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;

@Service
public class OrderApplicationService {

    private final OrderRepository orderRepository;
    private final InventoryServiceAcl inventoryAcl;
    private final PaymentServiceAcl paymentServiceAcl;
    private final OrderStateMachineService orderStateMachineService;

    public OrderApplicationService(OrderRepository orderRepository,
                                   InventoryServiceAcl inventoryAcl,
                                   PaymentServiceAcl paymentServiceAcl,
                                   OrderStateMachineService orderStateMachineService) {
        this.orderRepository = orderRepository;
        this.inventoryAcl = inventoryAcl;
        this.paymentServiceAcl = paymentServiceAcl;
        this.orderStateMachineService = orderStateMachineService;
    }

    /**
     * 创建订单
     */
    @Transactional
    public Long createOrder(OrderCreateCommand command) {
        if (!inventoryAcl.reserveStock(command.getProductId(), command.getQuantity())) {
            throw new RuntimeException("库存不足或服务不可用");
        }

        Order order = Order.create(command.getCustomerId(), Arrays.asList(
                ProductItem.create(command.getProductId(), command.getProductName(), command.getQuantity(), new Money(new BigDecimal(command.getPrice()), "CNY")
                )));

        orderRepository.save(order);

        return order.getId();
    }

    /**
     * 订单支付
     */
    @Transactional
    public String payOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("订单不存在"));

        if (order.getStatus() != OrderStatus.CREATED) {
            throw new IllegalStateException("只有待支付订单才能支付");
        }

        // 调用支付防腐层
        boolean paymentSuccess = paymentServiceAcl.processPayment(order);
        if (!paymentSuccess) {
            throw new RuntimeException("支付失败，请稍后重试");
        }

        // 触发状态机转换
        if (!orderStateMachineService.fire(orderId, OrderEvent.PAY)) {
            throw new RuntimeException("支付失败, 状态异常，订单号：" + order.getOrderNum());
        }

        return "支付成功，订单号：" + order.getOrderNum();
    }

    /**
     * 订单发货
     */
    @Transactional
    public String shipOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("订单不存在"));

        // 触发状态机转换
        if (!orderStateMachineService.fire(orderId, OrderEvent.SHIP)) {
            throw new RuntimeException("发货失败, 状态异常，订单号：" + order.getOrderNum());
        }

        return "发货成功，订单号：" + order.getOrderNum();
    }

    /**
     * 订单收货
     */
    @Transactional
    public String receiveOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("订单不存在"));

        // 触发状态机转换
        if (!orderStateMachineService.fire(orderId, OrderEvent.SIGN)) {
            throw new RuntimeException("收货失败, 状态异常，订单号：" + order.getOrderNum());
        }

        return "收货成功，订单号：" + order.getOrderNum();
    }

    /**
     * 订单取消
     */
    @Transactional
    public String cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("订单不存在"));
//        order.cancel();

        // 已支付订单必须先完成资金撤销；外部调用失败时不推进订单状态。
        if (order.getStatus() == OrderStatus.PAID) {
            boolean cancelPaymentSuccess = paymentServiceAcl.cancelPayment(order);
            if (!cancelPaymentSuccess) {
                throw new IllegalStateException("取消支付失败");
            }
        }

        if (!orderStateMachineService.fire(orderId, OrderEvent.CANCEL)) {
            throw new RuntimeException("取消失败, 状态异常，订单号：" + order.getOrderNum());
        }

        return "取消成功，订单号：" + order.getOrderNum();
    }

    /**
     * 获取订单状态
     */

    public String getOrderStatus(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("订单不存在"));

        return order.getStatus().name();
    }
}