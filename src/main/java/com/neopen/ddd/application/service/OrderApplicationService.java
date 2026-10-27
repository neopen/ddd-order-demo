package com.neopen.ddd.application.service;

import com.neopen.ddd.application.dto.OrderCreateCommand;
import com.neopen.ddd.domain.model.*;
import com.neopen.ddd.domain.repository.OrderRepository;
import com.neopen.ddd.domain.repository.OrderStatusHistoryRepository;
import com.neopen.ddd.domain.statemachine.OrderStateMachineService;
import com.neopen.ddd.infrastructure.integration.inventory.acl.InventoryServiceAcl;
import com.neopen.ddd.infrastructure.integration.payment.acl.PaymentServiceAcl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

@Service
public class OrderApplicationService {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final InventoryServiceAcl inventoryAcl;
    private final PaymentServiceAcl paymentServiceAcl;
    private final OrderStateMachineService orderStateMachineService;

    public OrderApplicationService(OrderRepository orderRepository, OrderStatusHistoryRepository historyRepository,
                                   InventoryServiceAcl inventoryAcl,
                                   PaymentServiceAcl paymentServiceAcl,
                                   OrderStateMachineService orderStateMachineService) {
        this.orderRepository = orderRepository;
        this.historyRepository = historyRepository;
        this.inventoryAcl = inventoryAcl;
        this.paymentServiceAcl = paymentServiceAcl;
        this.orderStateMachineService = orderStateMachineService;
    }

    /**
     * 创建订单
     * TODO 此处涉及多服务调用，要考虑分布式事务
     *
     * 库存与订单一致性：库存预占和数据库事务不属于同一个事务，需要补偿机制或 Saga。
     */
    @Transactional
    public Long createOrder(OrderCreateCommand command) {
        if (command.getCustomerId() == null) {
            throw new IllegalArgumentException("客户ID不能为空");
        }

        // 先在本地校验并构建聚合，避免明显非法的订单导致库存被预占。
        Order order = Order.create(command.getCustomerId(), Arrays.asList(
                ProductItem.create(command.getProductId(), command.getProductName(), command.getQuantity(),
                        new Money(command.getPrice(), "CNY"))));

        if (!inventoryAcl.reserveStock(command.getProductId(), command.getQuantity())) {
            throw new IllegalStateException("库存不足或库存服务不可用");
        }

        // 注意：库存预占与本地数据库不是同一个事务；生产环境应补偿释放或使用 Saga/Outbox。
        orderRepository.save(order);

        //        状态记录
        historyRepository.save(OrderStatusHistory.of(order, OrderEvent.CREATE, "创建订单"));

        return order.getId();
    }

    /**
     * 订单支付
     * TODO: 此处涉及多服务调用，要考虑分布式事务。 以及支付相关处理
     *
     * 支付与订单一致性：支付渠道成功后，数据库提交仍可能失败，需要支付幂等和异常补偿。
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
            throw new IllegalStateException("支付失败，请稍后重试");
        }

        // 保存支付结果信息，但不直接修改订单状态；状态仍由状态机决定。
        order.recordPayment(new OrderPayment(
                "PAY-" + order.getOrderNum(),
                "TXN-" + order.getOrderNum(),
                "COMPLETED",
                order));

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
     * 确认订单完成（签收后）。
     */
    @Transactional
    public String completeOrder(Long orderId) {
        orderStateMachineService.fire(orderId, OrderEvent.COMPLETE);
        return "订单已完成，订单ID：" + orderId;
    }

    /**
     * 申请退款。真正的退款渠道调用应在退款流程中异步处理并保证幂等。
     */
    @Transactional
    public String refundOrder(Long orderId) {
        orderStateMachineService.fire(orderId, OrderEvent.REFUND);
        return "退款申请已受理，订单ID：" + orderId;
    }

    /**
     * 记录退款渠道成功回执。
     */
    @Transactional
    public String refundSucceeded(Long orderId) {
        orderStateMachineService.fire(orderId, OrderEvent.REFUND_SUCCESS);
        return "退款已完成，订单ID：" + orderId;
    }

    /**
     * 记录退款渠道失败回执，并由状态机恢复退款前状态。
     */
    @Transactional
    public String refundFailed(Long orderId) {
        orderStateMachineService.fire(orderId, OrderEvent.REFUND_FAILED);
        return "退款失败，订单已恢复原状态，订单ID：" + orderId;
    }

    /**
     * 关闭待支付或已支付订单；已支付订单的资金撤销应先完成。
     */
    @Transactional
    public String closeOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在：" + orderId));
        if (order.getStatus() == OrderStatus.PAID) {
            if (!paymentServiceAcl.cancelPayment(order)) {
                throw new IllegalStateException("撤销支付失败，不能关闭订单");
            }
        }
        orderStateMachineService.fire(orderId, OrderEvent.CLOSE);
        return "订单已关闭，订单ID：" + orderId;
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