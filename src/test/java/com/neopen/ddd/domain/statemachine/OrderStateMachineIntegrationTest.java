
package com.neopen.ddd.domain.statemachine;

import com.neopen.ddd.OrderApplication;
import com.neopen.ddd.domain.model.Money;
import com.neopen.ddd.domain.model.Order;
import com.neopen.ddd.domain.model.OrderEvent;
import com.neopen.ddd.domain.model.OrderStatus;
import com.neopen.ddd.domain.model.ProductItem;
import com.neopen.ddd.domain.repository.OrderRepository;
import com.neopen.ddd.infrastructure.messaging.KafkaEventPublisher;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(
        classes = OrderApplication.class,
        properties = {
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.kafka.listener.auto-startup=false"
        }
)
class OrderStateMachineIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderStateMachineService stateMachineService;

    /**
     * 只模拟外部消息发布，不模拟订单状态机或数据库。
     */
    @MockBean
    private KafkaEventPublisher kafkaEventPublisher;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        reset(kafkaEventPublisher);
    }

    /**
     * 创建一个真实持久化的待支付订单。
     */
    private Order createOrder() {
        ProductItem item = ProductItem.create(
                1001L,
                "测试商品",
                2,
                new Money(new BigDecimal("50.00"), "CNY")
        );

        Order order = Order.create(
                10001L,
                List.of(item)
        );

        return orderRepository.saveAndFlush(order);
    }

    /**
     * 每次状态迁移后都从数据库重新读取，
     * 避免只验证内存中的对象。
     */
    private Order assertStatus(Long orderId, OrderStatus expected) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() ->
                        new AssertionError("订单不存在：" + orderId));

        assertEquals(expected, order.getStatus());
        return order;
    }

    @Test
    @DisplayName("完整流程：创建 -> 支付 -> 发货 -> 签收 -> 完成")
    void shouldCompleteOrderLifecycle() {
        // 1. 创建订单
        Order order = createOrder();
        Long orderId = order.getId();

        assertNotNull(orderId);
        assertEquals(new BigDecimal("100.00"), order.getTotalAmount());
        assertStatus(orderId, OrderStatus.CREATED);

        // 2. 支付成功：CREATED -> PAID
        assertTrue(stateMachineService.fire(orderId, OrderEvent.PAY));
        assertStatus(orderId, OrderStatus.PAID);

        // 3. 发货：PAID -> SHIPPED
        assertTrue(stateMachineService.fire(orderId, OrderEvent.SHIP));
        assertStatus(orderId, OrderStatus.SHIPPED);

        // 4. 签收：SHIPPED -> DELIVERED
        assertTrue(stateMachineService.fire(orderId, OrderEvent.SIGN));
        assertStatus(orderId, OrderStatus.DELIVERED);

        // 5. 完成：DELIVERED -> COMPLETED
        assertTrue(stateMachineService.fire(orderId, OrderEvent.COMPLETE));
        assertStatus(orderId, OrderStatus.COMPLETED);

        // 6. 最终状态不能继续发货
        assertThrows(
                IllegalStateException.class,
                () -> stateMachineService.fire(orderId, OrderEvent.SHIP)
        );

        assertStatus(orderId, OrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("取消订单后不能再次支付")
    void shouldRejectPayAfterOrderCancelled() {
        Order order = createOrder();
        Long orderId = order.getId();

        // CREATED -> CANCELLED
        assertTrue(
                stateMachineService.fire(orderId, OrderEvent.CANCEL)
        );
        assertStatus(orderId, OrderStatus.CANCELLED);

        // CANCELLED -> PAID 必须失败
        assertThrows(
                IllegalStateException.class,
                () -> stateMachineService.fire(orderId, OrderEvent.PAY)
        );

        // 数据库状态必须保持 CANCELLED
        assertStatus(orderId, OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("待支付订单可以直接取消")
    void shouldCancelCreatedOrder() {
        Order order = createOrder();
        Long orderId = order.getId();

        assertTrue(stateMachineService.fire(
                orderId, OrderEvent.CANCEL));

        assertStatus(orderId, OrderStatus.CANCELLED);

        // 终态订单不应再次支付
        assertThrows(
                IllegalStateException.class,
                () -> stateMachineService.fire(orderId, OrderEvent.PAY)
        );

        assertStatus(orderId, OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("退款成功：已支付 -> 退款中 -> 已退款")
    void shouldCompleteRefundSuccessfully() {
        Order order = createOrder();
        Long orderId = order.getId();

        // 先完成支付
        stateMachineService.fire(orderId, OrderEvent.PAY);
        assertStatus(orderId, OrderStatus.PAID);

        // 申请退款
        stateMachineService.fire(orderId, OrderEvent.REFUND);

        Order refundingOrder = assertStatus(
                orderId, OrderStatus.REFUNDING);

        assertEquals(OrderStatus.PAID, refundingOrder.getPreStatus());

        // 退款渠道确认成功
        stateMachineService.fire(
                orderId, OrderEvent.REFUND_SUCCESS);

        Order refundedOrder = assertStatus(
                orderId, OrderStatus.REFUNDED);

        assertNull(refundedOrder.getPreStatus());

        // 退款完成后不能再次申请退款
        assertThrows(
                IllegalStateException.class,
                () -> stateMachineService.fire(
                        orderId, OrderEvent.REFUND)
        );

        assertStatus(orderId, OrderStatus.REFUNDED);
    }

    @Test
    @DisplayName("退款失败：恢复到申请退款前的订单状态")
    void shouldRestorePreviousStatusWhenRefundFails() {
        Order order = createOrder();
        Long orderId = order.getId();

        // CREATED -> PAID -> SHIPPED
        stateMachineService.fire(orderId, OrderEvent.PAY);
        stateMachineService.fire(orderId, OrderEvent.SHIP);

        assertStatus(orderId, OrderStatus.SHIPPED);

        // SHIPPED -> REFUNDING
        stateMachineService.fire(orderId, OrderEvent.REFUND);

        Order refundingOrder = assertStatus(
                orderId, OrderStatus.REFUNDING);

        assertEquals(
                OrderStatus.SHIPPED,
                refundingOrder.getPreStatus()
        );

        // REFUNDING -> SHIPPED
        stateMachineService.fire(
                orderId, OrderEvent.REFUND_FAILED);

        Order restoredOrder = assertStatus(
                orderId, OrderStatus.SHIPPED);

        assertNull(restoredOrder.getPreStatus());
    }

    @Test
    @DisplayName("已签收订单退款失败后应恢复到 DELIVERED")
    void shouldRestoreDeliveredStatusWhenRefundFails() {
        Order order = createOrder();
        Long orderId = order.getId();

        stateMachineService.fire(orderId, OrderEvent.PAY);
        stateMachineService.fire(orderId, OrderEvent.SHIP);
        stateMachineService.fire(orderId, OrderEvent.SIGN);

        assertStatus(orderId, OrderStatus.DELIVERED);

        stateMachineService.fire(orderId, OrderEvent.REFUND);
        assertStatus(orderId, OrderStatus.REFUNDING);

        stateMachineService.fire(
                orderId, OrderEvent.REFUND_FAILED);

        Order restoredOrder = assertStatus(
                orderId, OrderStatus.DELIVERED);

        assertNull(restoredOrder.getPreStatus());
    }

    @Test
    @DisplayName("非法事件不能修改数据库中的订单状态")
    void shouldRejectInvalidTransitionWithoutChangingOrder() {
        Order order = createOrder();
        Long orderId = order.getId();

        assertStatus(orderId, OrderStatus.CREATED);

        // CREATED 状态不允许直接发货
        assertThrows(
                IllegalStateException.class,
                () -> stateMachineService.fire(
                        orderId, OrderEvent.SHIP)
        );

        // 状态必须保持不变
        assertStatus(orderId, OrderStatus.CREATED);
    }

    @Test
    @DisplayName("订单不存在时应抛出异常")
    void shouldThrowWhenOrderDoesNotExist() {
        assertThrows(
                IllegalArgumentException.class,
                () -> stateMachineService.fire(
                        Long.MAX_VALUE, OrderEvent.PAY)
        );
    }

    @Test
    @DisplayName("空订单ID或空事件应被拒绝")
    void shouldRejectNullArguments() {
        assertThrows(
                IllegalArgumentException.class,
                () -> stateMachineService.fire(null, OrderEvent.PAY)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> stateMachineService.fire(1L, null)
        );
    }
}
