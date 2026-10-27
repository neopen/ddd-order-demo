package com.neopen.ddd.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

    private Order newOrder() {
        return Order.create(1L, List.of(ProductItem.create(
                10L, "测试商品", 2, new Money(new BigDecimal("12.50"), "CNY"))));
    }

    @Test
    void refundFailureShouldRestorePreviousStatus() {
        Order order = newOrder();
        order.transitTo(OrderStatus.PAID);
        order.transitTo(OrderStatus.SHIPPED);
        order.transitTo(OrderStatus.REFUNDING);

        assertEquals(OrderStatus.REFUNDING, order.getStatus());
        assertEquals(OrderStatus.SHIPPED, order.getPreStatus());

        order.transitTo(OrderStatus.SHIPPED);
        assertEquals(OrderStatus.SHIPPED, order.getStatus());
        assertNull(order.getPreStatus());
    }

    @Test
    void successfulRefundShouldClearPreviousStatus() {
        Order order = newOrder();
        order.transitTo(OrderStatus.PAID);
        order.transitTo(OrderStatus.REFUNDING);
        order.transitTo(OrderStatus.REFUNDED);

        assertEquals(OrderStatus.REFUNDED, order.getStatus());
        assertNull(order.getPreStatus());
    }

    @Test
    void recordingPaymentShouldNotChangeOrderStatus() {
        Order order = newOrder();
        OrderPayment payment = new OrderPayment("p-1", "txn-1", "COMPLETED", order);
        order.recordPayment(payment);

        assertEquals(OrderStatus.CREATED, order.getStatus());
        assertSame(payment, order.getOrderPayment());
        assertEquals(order, payment.getOrder());
        assertThrows(IllegalStateException.class, () -> order.recordPayment(payment));
    }

    @Test
    void onlyCreatedOrderCanModifyItems() {
        Order order = newOrder();
        order.transitTo(OrderStatus.PAID);
        assertThrows(IllegalStateException.class, () -> order.addOrderItem(
                ProductItem.create(11L, "其他商品", 1, new Money(new BigDecimal("1.00"), "CNY"))));
    }
}
