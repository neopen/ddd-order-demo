
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

/**
 * 订单状态机集成测试：验证合法迁移、非法迁移、取消以及退款成功/失败的恢复规则。
 * <p>测试范围：启动真实 Spring 容器与真实数据库，由状态机服务驱动订单状态变更；
 * 每个用例都通过仓储重新读取订单，验证状态确实落库，而非只修改了内存对象。</p>
 * <p>Mock 策略：仅用 {@code @MockBean} 隔离 Kafka 事件发布器，避免测试向消息中间件投递；
 * 状态机、仓储与事务均使用真实实现。建表策略为 create-drop，每个用例前清空订单表。</p>
 */
@SpringBootTest(
        classes = OrderApplication.class,
        properties = {
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.kafka.listener.auto-startup=false"
        }
)
class OrderStateMachineIntegrationTest {

    /** 订单仓储：用于准备测试数据，并在状态迁移后重新读取订单。 */
    @Autowired
    private OrderRepository orderRepository;

    /** 被测的状态机应用服务：负责校验迁移、落库状态与历史、发布领域事件。 */
    @Autowired
    private OrderStateMachineService stateMachineService;

    /**
     * 只模拟外部消息发布，不模拟订单状态机或数据库。
     */
    @MockBean
    private KafkaEventPublisher kafkaEventPublisher;

    /**
     * 用例前置：清空订单表并重置 Kafka 发布器的打桩记录，保证用例之间互不影响。
     */
    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        reset(kafkaEventPublisher);
    }

    /**
     * 创建一个真实持久化的待支付订单。
     * <p>数据固定为客户 10001、单价 50 元、数量 2，订单总额 100 元，便于金额断言。</p>
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
     * <p>同时返回重新加载的订单，便于继续断言退款前状态等字段。</p>
     */
    private Order assertStatus(Long orderId, OrderStatus expected) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() ->
                        new AssertionError("订单不存在：" + orderId));

        assertEquals(expected, order.getStatus());
        return order;
    }

    /**
     * 业务场景：订单按标准生命周期依次完成支付、发货、签收与完成。
     * <p>关键断言：每次迁移后数据库中的状态与目标状态一致；进入终态后再次发货抛出
     * IllegalStateException，且状态保持 COMPLETED 不变。</p>
     */
    @Test
    @DisplayName("完整流程：创建 -> 支付 -> 发货 -> 签收 -> 完成")
    void shouldCompleteOrderLifecycle() {
        // 准备：创建待支付订单，并确认初始金额与状态
        // 1. 创建订单
        Order order = createOrder();
        Long orderId = order.getId();

        assertNotNull(orderId);
        assertEquals(new Money(new BigDecimal("100.00"), "CNY"), order.getTotalAmount());
        assertStatus(orderId, OrderStatus.CREATED);

        // 执行：依次触发支付、发货、签收、完成事件
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

        // 断言：终态不再接受发货，且数据库状态保持 COMPLETED
        // 6. 最终状态不能继续发货
        assertThrows(
                IllegalStateException.class,
                () -> stateMachineService.fire(orderId, OrderEvent.SHIP)
        );

        assertStatus(orderId, OrderStatus.COMPLETED);
    }

    /**
     * 业务场景：订单先被取消，随后尝试支付。
     * <p>关键断言：取消成功并落库为 CANCELLED；支付抛出 IllegalStateException，
     * 数据库状态仍为 CANCELLED，说明非法迁移不会污染已有状态。</p>
     */
    @Test
    @DisplayName("取消订单后不能再次支付")
    void shouldRejectPayAfterOrderCancelled() {
        // 准备：创建待支付订单
        Order order = createOrder();
        Long orderId = order.getId();

        // 执行：先取消，再尝试支付这一非法迁移
        // CREATED -> CANCELLED
        assertTrue(
                stateMachineService.fire(orderId, OrderEvent.CANCEL)
        );
        assertStatus(orderId, OrderStatus.CANCELLED);

        // 断言：非法迁移被拒绝，数据库状态未发生变化
        // CANCELLED -> PAID 必须失败
        assertThrows(
                IllegalStateException.class,
                () -> stateMachineService.fire(orderId, OrderEvent.PAY)
        );

        // 数据库状态必须保持 CANCELLED
        assertStatus(orderId, OrderStatus.CANCELLED);
    }

    /**
     * 业务场景：待支付的订单直接取消。
     * <p>关键断言：取消成功并落库为 CANCELLED；取消后再次支付被拒绝，
     * 状态仍为 CANCELLED。</p>
     */
    @Test
    @DisplayName("待支付订单可以直接取消")
    void shouldCancelCreatedOrder() {
        // 准备：创建待支付订单
        Order order = createOrder();
        Long orderId = order.getId();

        // 执行：对待支付订单触发取消事件
        assertTrue(stateMachineService.fire(
                orderId, OrderEvent.CANCEL));

        // 断言：状态已落库为 CANCELLED，且终态拒绝支付
        assertStatus(orderId, OrderStatus.CANCELLED);

        // 终态订单不应再次支付
        assertThrows(
                IllegalStateException.class,
                () -> stateMachineService.fire(orderId, OrderEvent.PAY)
        );

        assertStatus(orderId, OrderStatus.CANCELLED);
    }

    /**
     * 业务场景：已支付订单申请退款并由渠道确认成功。
     * <p>关键断言：进入退款中时记录的前置状态为 PAID；确认退款后状态为 REFUNDED，
     * 退款前状态被清空；已退款终态再次申请退款会被拒绝。</p>
     */
    @Test
    @DisplayName("退款成功：已支付 -> 退款中 -> 已退款")
    void shouldCompleteRefundSuccessfully() {
        // 准备：创建订单并支付，使其进入可退款状态
        Order order = createOrder();
        Long orderId = order.getId();

        // 先完成支付
        stateMachineService.fire(orderId, OrderEvent.PAY);
        assertStatus(orderId, OrderStatus.PAID);

        // 执行：先申请退款，再提交退款成功回执
        // 申请退款
        stateMachineService.fire(orderId, OrderEvent.REFUND);

        Order refundingOrder = assertStatus(
                orderId, OrderStatus.REFUNDING);

        // 断言：进入退款中时记录了前置状态 PAID
        assertEquals(OrderStatus.PAID, refundingOrder.getPreStatus());

        // 退款渠道确认成功
        stateMachineService.fire(
                orderId, OrderEvent.REFUND_SUCCESS);

        Order refundedOrder = assertStatus(
                orderId, OrderStatus.REFUNDED);

        // 断言：确认退款后进入终态，且前置状态被清空
        assertNull(refundedOrder.getPreStatus());

        // 退款完成后不能再次申请退款
        assertThrows(
                IllegalStateException.class,
                () -> stateMachineService.fire(
                        orderId, OrderEvent.REFUND)
        );

        assertStatus(orderId, OrderStatus.REFUNDED);
    }

    /**
     * 业务场景：已发货订单申请退款，但渠道返回退款失败。
     * <p>关键断言：进入退款中时记录前置状态 SHIPPED；失败回执后恢复为 SHIPPED，
     * 并清空退款前状态，保证订单回到申请退款前的业务位置。</p>
     */
    @Test
    @DisplayName("退款失败：恢复到申请退款前的订单状态")
    void shouldRestorePreviousStatusWhenRefundFails() {
        // 准备：创建订单并推进到已发货状态
        Order order = createOrder();
        Long orderId = order.getId();

        // CREATED -> PAID -> SHIPPED
        stateMachineService.fire(orderId, OrderEvent.PAY);
        stateMachineService.fire(orderId, OrderEvent.SHIP);

        assertStatus(orderId, OrderStatus.SHIPPED);

        // 执行：申请退款后提交退款失败回执
        // SHIPPED -> REFUNDING
        stateMachineService.fire(orderId, OrderEvent.REFUND);

        Order refundingOrder = assertStatus(
                orderId, OrderStatus.REFUNDING);

        // 断言：退款中记录原状态，失败后恢复到原状态并清空记录
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

    /**
     * 业务场景：已签收订单申请退款，但渠道返回退款失败。
     * <p>关键断言：失败后订单恢复为 DELIVERED 而非更早状态，退款前状态被清空。</p>
     */
    @Test
    @DisplayName("已签收订单退款失败后应恢复到 DELIVERED")
    void shouldRestoreDeliveredStatusWhenRefundFails() {
        // 准备：创建订单并推进到已签收状态
        Order order = createOrder();
        Long orderId = order.getId();

        stateMachineService.fire(orderId, OrderEvent.PAY);
        stateMachineService.fire(orderId, OrderEvent.SHIP);
        stateMachineService.fire(orderId, OrderEvent.SIGN);

        assertStatus(orderId, OrderStatus.DELIVERED);

        // 执行：申请退款后提交退款失败回执
        stateMachineService.fire(orderId, OrderEvent.REFUND);
        assertStatus(orderId, OrderStatus.REFUNDING);

        stateMachineService.fire(
                orderId, OrderEvent.REFUND_FAILED);

        // 断言：恢复目标取申请退款前的状态 DELIVERED
        Order restoredOrder = assertStatus(
                orderId, OrderStatus.DELIVERED);

        assertNull(restoredOrder.getPreStatus());
    }

    /**
     * 业务场景：待支付订单直接触发发货这一非法迁移。
     * <p>关键断言：抛出 IllegalStateException，且数据库状态仍为 CREATED，
     * 说明失败的迁移不会留下副作用。</p>
     */
    @Test
    @DisplayName("非法事件不能修改数据库中的订单状态")
    void shouldRejectInvalidTransitionWithoutChangingOrder() {
        // 准备：创建待支付订单并确认初始状态
        Order order = createOrder();
        Long orderId = order.getId();

        assertStatus(orderId, OrderStatus.CREATED);

        // 执行：在 CREATED 状态触发发货事件
        // CREATED 状态不允许直接发货
        assertThrows(
                IllegalStateException.class,
                () -> stateMachineService.fire(
                        orderId, OrderEvent.SHIP)
        );

        // 断言：失败后数据库仍停留在初始状态
        // 状态必须保持不变
        assertStatus(orderId, OrderStatus.CREATED);
    }

    /**
     * 业务场景：对不存在的订单触发状态迁移。
     * <p>关键断言：抛出 IllegalArgumentException，提示订单标识无效。</p>
     */
    @Test
    @DisplayName("订单不存在时应抛出异常")
    void shouldThrowWhenOrderDoesNotExist() {
        // 准备：数据库中不存在主键为 Long.MAX_VALUE 的订单
        // 执行：使用不存在的订单 ID 触发支付
        assertThrows(
                IllegalArgumentException.class,
                () -> stateMachineService.fire(
                        Long.MAX_VALUE, OrderEvent.PAY)
        );
    }

    /**
     * 业务场景：调用状态机时传入空订单 ID 或空事件。
     * <p>关键断言：两种非法参数都在进入状态机前抛出 IllegalArgumentException，
     * 避免后续仓储查询或状态判断出现歧义。</p>
     */
    @Test
    @DisplayName("空订单ID或空事件应被拒绝")
    void shouldRejectNullArguments() {
        // 准备：不需要任何订单数据，参数校验先于仓储访问
        // 执行：分别传入空订单 ID 与空事件
        assertThrows(
                IllegalArgumentException.class,
                () -> stateMachineService.fire(null, OrderEvent.PAY)
        );

        // 断言：空事件同样被参数校验拦截
        assertThrows(
                IllegalArgumentException.class,
                () -> stateMachineService.fire(1L, null)
        );
    }
}
