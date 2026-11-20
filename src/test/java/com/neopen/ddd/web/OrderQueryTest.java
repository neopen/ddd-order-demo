package com.neopen.ddd.web;

import com.neopen.ddd.application.dto.OrderDetailView;
import com.neopen.ddd.application.dto.OrderQuery;
import com.neopen.ddd.application.dto.OrderSummaryView;
import com.neopen.ddd.application.dto.PageResult;
import com.neopen.ddd.application.service.OrderQueryService;
import com.neopen.ddd.domain.model.Money;
import com.neopen.ddd.domain.model.Order;
import com.neopen.ddd.domain.model.ProductItem;
import com.neopen.ddd.domain.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 订单查询服务集成测试：覆盖按客户分页查询与订单详情两个读侧用例。
 * <p>测试范围：验证命令查询职责分离 (CQRS) 读侧对聚合数据的过滤与视图映射，
 * 不涉及写命令、定价与状态机。</p>
 * <p>Mock 策略：不使用 Mock，启动真实 Spring 容器并访问真实仓储与数据库；
 * 用例由 @Transactional 包裹，结束后回滚数据，避免相互污染。</p>
 */
@SpringBootTest
@Transactional
class OrderQueryTest {

    /** 订单仓储：用于直接落库测试数据，模拟已有订单。 */
    @Autowired
    private OrderRepository orderRepository;

    /** 被测的查询服务：验证列表分页与详情视图的组装结果。 */
    @Autowired
    private OrderQueryService orderQueryService;

    /**
     * 业务场景：某客户已有订单，按客户 ID 查询订单列表。
     * <p>关键断言：分页结果非空，内容包含刚创建的订单，且总数至少为 1。</p>
     */
    @Test
    @DisplayName("按客户ID分页查询订单列表")
    void shouldQueryOrderList() {
        // 准备：持久化一笔属于客户 1001 的订单
        Order order = createOrder(1001L);

        OrderQuery query = new OrderQuery();
        query.setCustomerId(1001L);
        query.setPage(0);
        query.setSize(10);

        // 执行：按客户过滤，取第 0 页、每页 10 条
        PageResult<OrderSummaryView> result = orderQueryService.list(query);

        // 断言：列表包含目标订单，总数覆盖该订单
        assertNotNull(result);
        assertTrue(result.content().stream()
                .anyMatch(view -> view.id().equals(order.getId())));
        assertTrue(result.totalElements() >= 1);
    }

    /**
     * 业务场景：查询一笔未支付订单的详情。
     * <p>关键断言：订单号、状态、客户与订单项数量与落库数据一致，应付金额为 25.00，
     * 且未支付时支付信息为空。</p>
     */
    @Test
    @DisplayName("查询订单详情包含订单项")
    void shouldQueryOrderDetail() {
        // 准备：持久化一笔客户 1002 的待支付订单
        Order order = createOrder(1002L);

        // 执行：按订单主键读取详情视图
        OrderDetailView detail = orderQueryService.detail(order.getId());

        // 断言：视图字段与聚合快照一致，支付前的 payment 为空
        assertEquals(order.getOrderNum(), detail.orderNum());
        assertEquals("CREATED", detail.status());
        assertEquals(1002L, detail.customerId());
        assertEquals(1, detail.items().size());
        assertEquals(new BigDecimal("25.00"), detail.payableAmount().amount());
        assertNull(detail.payment());
    }

    /**
     * 构造并持久化一笔待支付订单：单价 12.50、数量 2，应付 25.00。
     *
     * @param customerId 下单客户 ID
     * @return 已 flush 的订单，保证随后的查询能够读到
     */
    private Order createOrder(Long customerId) {
        Order order = Order.create(customerId, List.of(
                ProductItem.create(
                        10L,
                        "测试商品",
                        2,
                        new Money(new BigDecimal("12.50"), "CNY"))));
        return orderRepository.saveAndFlush(order);
    }
}
