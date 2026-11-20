package com.neopen.ddd.web;

import com.neopen.ddd.application.saga.SagaInstance;
import com.neopen.ddd.application.saga.SagaInstanceRepository;
import com.neopen.ddd.application.saga.SagaStatus;
import com.neopen.ddd.application.saga.SagaStepRepository;
import com.neopen.ddd.domain.repository.OrderRepository;
import com.neopen.ddd.infrastructure.integration.inventory.acl.InventoryServiceAcl;
import com.neopen.ddd.infrastructure.integration.payment.acl.PaymentServiceAcl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Saga 补偿链路集成测试：验证“库存已预占、订单落库失败”时的反向补偿。
 *
 * <p>故障注入方式：{@code app.saga.fault-step=PERSIST_ORDER} 让 Saga 在持久化步骤前抛出异常，
 * 模拟数据库写入失败。由于库存预占已经成功，编排器必须释放预占单并把 Saga 标记为 COMPENSATED。</p>
 *
 * <p>测试使用独立 H2 库，避免与其他测试上下文共享同名内存库造成建表冲突；
 * 不使用 {@code @Transactional}，以便观察 Saga 轨迹在业务事务回滚后依然存在。</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:sagadb;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.saga.fault-step=PERSIST_ORDER",
        "app.outbox.relay-enabled=false",
        "spring.kafka.listener.auto-startup=false"
})
@AutoConfigureMockMvc
class OrderSagaCompensationTest {

    /** 创建订单请求体：单商品，用于触发 Saga。 */
    private static final String CREATE_BODY = """
            {
              "customerId": 3001,
              "items": [
                {"productId": 21, "productName": "测试商品", "quantity": 2, "price": 60.00}
              ]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SagaInstanceRepository sagaInstanceRepository;

    @Autowired
    private SagaStepRepository sagaStepRepository;

    @Autowired
    private OrderRepository orderRepository;

    @MockBean
    private InventoryServiceAcl inventoryAcl;

    @MockBean
    private PaymentServiceAcl paymentServiceAcl;

    @BeforeEach
    void setUp() {
        when(inventoryAcl.reserveStock(anyLong(), anyInt(), anyString())).thenReturn("RSV-FAULT-001");
        when(inventoryAcl.releaseStock(anyString())).thenReturn(true);
    }

    /**
     * 场景：库存预占成功，订单持久化被故障注入阻断。
     * <p>断言：接口返回冲突状态；库存预占被释放；订单未落库；
     * Saga 状态为 COMPENSATED；RESERVE_STOCK 成功而 PERSIST_ORDER 失败。</p>
     */
    @Test
    @DisplayName("持久化失败时释放库存并完成 Saga 补偿")
    void shouldCompensateReservationWhenPersistFails() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isConflict());

        verify(inventoryAcl).reserveStock(eq(21L), eq(2), anyString());
        verify(inventoryAcl).releaseStock("RSV-FAULT-001");
        assertEquals(0, orderRepository.count());

        SagaInstance saga = sagaInstanceRepository.findAll().get(0);
        assertEquals(SagaStatus.COMPENSATED, saga.getStatus());
        assertTrue(sagaStepRepository.findBySagaIdAndStepName(saga.getSagaId(), "RESERVE_STOCK")
                .orElseThrow()
                .getStatus()
                .name()
                .equals("SUCCEEDED"));
        assertEquals("FAILED", sagaStepRepository
                .findBySagaIdAndStepName(saga.getSagaId(), "PERSIST_ORDER")
                .orElseThrow()
                .getStatus()
                .name());
    }
}
