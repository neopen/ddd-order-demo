package com.neopen.ddd.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 订单聚合根单元测试：验证退款状态恢复、支付记录与订单项修改等聚合不变量。
 * <p>测试范围：纯内存对象行为，不启动 Spring 容器、不访问数据库。</p>
 * <p>Mock 策略：不使用任何 Mock，聚合依赖的金额与订单项均为真实值对象
 * (Value Object)，直接验证聚合自身的规则。</p>
 */
class OrderTest {

    /**
     * 构造一笔待支付订单：客户 1，单价 12.50、数量 2，合计 25.00。
     *
     * @return 新建的订单聚合根，尚未持久化
     */
    private Order newOrder() {
        return Order.create(1L, List.of(ProductItem.create(
                10L, "测试商品", 2, new Money(new BigDecimal("12.50"), "CNY"))));
    }

    /**
     * 业务场景：已发货订单申请退款后，渠道返回退款失败。
     * <p>关键断言：退款中记录前置状态 SHIPPED；恢复到 SHIPPED 后前置状态被清空，
     * 保证聚合不会残留过期的退款上下文。</p>
     */
    @Test
    void refundFailureShouldRestorePreviousStatus() {
        // 准备：把订单推进到退款中，此时前置状态应为已发货
        Order order = newOrder();
        order.transitTo(OrderStatus.PAID);
        order.transitTo(OrderStatus.SHIPPED);
        order.transitTo(OrderStatus.REFUNDING);

        // 断言：退款中保存了原状态
        assertEquals(OrderStatus.REFUNDING, order.getStatus());
        assertEquals(OrderStatus.SHIPPED, order.getPreStatus());

        // 执行并断言：恢复到原状态后清空前置状态
        order.transitTo(OrderStatus.SHIPPED);
        assertEquals(OrderStatus.SHIPPED, order.getStatus());
        assertNull(order.getPreStatus());
    }

    /**
     * 业务场景：已支付订单申请退款并确认成功。
     * <p>关键断言：订单进入 REFUNDED 终态，退款前状态被清空。</p>
     */
    @Test
    void successfulRefundShouldClearPreviousStatus() {
        // 准备并执行：支付后申请退款，再由渠道确认成功
        Order order = newOrder();
        order.transitTo(OrderStatus.PAID);
        order.transitTo(OrderStatus.REFUNDING);
        order.transitTo(OrderStatus.REFUNDED);

        // 断言：终态为已退款，且不再保留退款前状态
        assertEquals(OrderStatus.REFUNDED, order.getStatus());
        assertNull(order.getPreStatus());
    }

    /**
     * 业务场景：待支付订单记录支付渠道回执。
     * <p>关键断言：记录支付不改变订单状态，聚合与支付记录互相持有引用；
     * 重复记录同一支付信息会抛出 IllegalStateException。</p>
     */
    @Test
    void recordingPaymentShouldNotChangeOrderStatus() {
        // 准备：构造一条支付记录，并建立在待支付订单上
        Order order = newOrder();
        OrderPayment payment = new OrderPayment("p-1", "txn-1", "COMPLETED", order);

        // 执行：记录支付回执
        order.recordPayment(payment);

        // 断言：状态不变，双向关联建立，且一个订单只允许一条支付记录
        assertEquals(OrderStatus.CREATED, order.getStatus());
        assertSame(payment, order.getOrderPayment());
        assertEquals(order, payment.getOrder());
        assertThrows(IllegalStateException.class, () -> order.recordPayment(payment));
    }

    /**
     * 业务场景：已支付订单尝试新增订单项。
     * <p>关键断言：聚合拒绝修改并抛出 IllegalStateException，
     * 保证订单项只在待支付阶段可变。</p>
     */
    @Test
    void onlyCreatedOrderCanModifyItems() {
        // 准备：创建订单并推进到已支付状态
        Order order = newOrder();
        order.transitTo(OrderStatus.PAID);

        // 执行并断言：新增订单项被聚合拒绝
        assertThrows(IllegalStateException.class, () -> order.addOrderItem(
                ProductItem.create(11L, "其他商品", 1, new Money(new BigDecimal("1.00"), "CNY"))));
    }
}
