package com.neopen.ddd.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neopen.ddd.domain.model.Order;
import com.neopen.ddd.infrastructure.integration.inventory.acl.InventoryServiceAcl;
import com.neopen.ddd.infrastructure.integration.payment.acl.PaymentServiceAcl;
import com.neopen.ddd.infrastructure.messaging.outbox.OutboxEvent;
import com.neopen.ddd.infrastructure.messaging.outbox.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 订单创建与支付完整流程集成测试：
 * HTTP 接口 -> 应用服务 -> 定价领域服务 -> 状态机 -> 仓储 -> Outbox。
 * 库存与支付外部服务通过 Mock 隔离，保证测试可重复执行。
 *
 * <p>测试范围：覆盖“创建订单 -> 支付订单”主链路，包括定价、接口幂等
 * (Idempotency)、状态机迁移、状态历史、事务性发件箱 (Transactional Outbox)
 * 落库，以及防腐层 (ACL) 的调用次数。</p>
 * <p>Mock 策略：仅用 {@code @MockBean} 替换库存与支付两个 ACL，使外部调用结果可控；
 * 应用服务、定价、状态机、仓储与数据库均使用真实实现。测试方法由 @Transactional
 * 包裹，结束后回滚订单、历史与发件箱数据，确保可重复执行。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderCreatePayFlowIntegrationTest {

    /** 创建订单的请求体：单商品 100 元，用于验证满 100 减 10 的满减策略。 */
    private static final String CREATE_BODY = """
            {
              "customerId": 2001,
              "items": [
                {"productId": 11, "productName": "机械键盘", "quantity": 1, "price": 100.00}
              ]
            }
            """;

    /** MockMvc 客户端：在容器内直接驱动 HTTP 接口，不经过真实网络。 */
    @Autowired
    private MockMvc mockMvc;

    /** JSON 解析器：从创建响应中提取订单 ID 与订单号，供后续步骤复用。 */
    @Autowired
    private ObjectMapper objectMapper;

    /** 发件箱仓储：读取事件记录，验证领域事件与业务数据同事务写入。 */
    @Autowired
    private OutboxEventRepository outboxEventRepository;

    /** 库存防腐层替身：预占与释放固定返回成功，隔离真实库存服务。 */
    @MockBean
    private InventoryServiceAcl inventoryAcl;

    /** 支付防腐层替身：支付与撤销固定返回成功，隔离真实支付渠道。 */
    @MockBean
    private PaymentServiceAcl paymentServiceAcl;

    /**
     * 用例前置：把外部协作统一打桩为成功，使断言聚焦于本地业务流程。
     * <p>Spring 会在每个测试方法后重置 Mock，这里重新声明桩，
     * 避免依赖测试执行顺序。</p>
     */
    @BeforeEach
    void setUp() {
        when(inventoryAcl.reserveStock(anyLong(), anyInt())).thenReturn(true);
        when(inventoryAcl.releaseStock(anyLong(), anyInt())).thenReturn(true);
        when(paymentServiceAcl.processPayment(any(Order.class))).thenReturn(true);
        when(paymentServiceAcl.cancelPayment(any(Order.class))).thenReturn(true);
    }

    /**
     * 业务场景：客户下单并完成支付，验证主链路的定价、幂等、状态、历史与事件。
     * <p>验证步骤与意图：</p>
     * <ol>
     *   <li>创建订单：断言满 100 减 10 的定价结果与 CREATED 状态；</li>
     *   <li>重复创建：相同幂等键返回同一订单，且不重复预占库存；</li>
     *   <li>支付订单：断言状态机把订单迁移为 PAID；</li>
     *   <li>重复支付：相同幂等键回放首次结果，不重复调用支付渠道；</li>
     *   <li>查询详情：核对应付金额、库存预占标记与支付记录；</li>
     *   <li>状态历史：断言 CREATE 与 PAY 两条记录及前后状态；</li>
     *   <li>ACL 调用次数：库存预占恰好一次、支付恰好一次；</li>
     *   <li>事务性发件箱：至少包含订单创建与状态变更两类事件。</li>
     * </ol>
     */
    @Test
    @DisplayName("创建订单并支付：定价、幂等、状态机、历史与 Outbox 全链路")
    void shouldCreateAndPayOrder() throws Exception {
        // 准备：ACL 桩见 setUp，创建与支付分别使用固定幂等键
        // 1. 创建订单：满 100 减 10，应付 90
        MvcResult createResult = mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "flow-create-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.totalAmount.amount").value(100.0))
                .andExpect(jsonPath("$.discountAmount.amount").value(10.0))
                .andExpect(jsonPath("$.payableAmount.amount").value(90.0))
                .andExpect(jsonPath("$.discountPolicy").value("FULL_REDUCTION"))
                .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long orderId = created.get("orderId").asLong();
        String orderNum = created.get("orderNum").asText();
        assertTrue(orderId > 0);
        assertFalse(orderNum.isBlank());

        // 2. 相同幂等键重复创建，返回同一订单，不重复预占库存
        mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "flow-create-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(orderId));

        // 执行：同一订单连续支付两次，第二次用于验证幂等回放
        // 3. 支付订单
        mockMvc.perform(post("/api/orders/{id}/pay", orderId)
                        .header("Idempotency-Key", "flow-pay-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.message").value("支付成功"));

        // 4. 相同幂等键重复支付，返回首次结果
        mockMvc.perform(post("/api/orders/{id}/pay", orderId)
                        .header("Idempotency-Key", "flow-pay-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.message").value("支付成功"));

        // 断言：查询快照、状态历史、ACL 次数与发件箱事件共同证明最终一致
        // 5. 查询详情：状态、支付金额、库存预占状态
        mockMvc.perform(get("/api/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.payableAmount.amount").value(90.0))
                .andExpect(jsonPath("$.stockReserved").value(true))
                .andExpect(jsonPath("$.payment.status").value("COMPLETED"))
                .andExpect(jsonPath("$.payment.amount.amount").value(90.0));

        // 6. 状态历史：CREATE、PAY 两条记录
        MvcResult historyResult = mockMvc.perform(get("/api/orders/{id}/history", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].event").value("CREATE"))
                .andExpect(jsonPath("$[0].toStatus").value("CREATED"))
                .andExpect(jsonPath("$[1].event").value("PAY"))
                .andExpect(jsonPath("$[1].fromStatus").value("CREATED"))
                .andExpect(jsonPath("$[1].toStatus").value("PAID"))
                .andReturn();
        assertNotNull(historyResult.getResponse().getContentAsString());

        // 7. 外部协作验证：库存预占一次，支付使用应付金额
        verify(inventoryAcl).reserveStock(11L, 1);
        verify(paymentServiceAcl).processPayment(any(Order.class));

        // 8. Outbox 中至少包含订单创建与状态变更两类事件
        List<OutboxEvent> events = outboxEventRepository.findAll();
        assertTrue(events.size() >= 2);
        assertTrue(events.stream().anyMatch(e -> e.getEventType().equals("OrderCreatedEvent")));
        assertTrue(events.stream().anyMatch(e -> e.getEventType().equals("OrderStatusChangedEvent")));
    }
}
