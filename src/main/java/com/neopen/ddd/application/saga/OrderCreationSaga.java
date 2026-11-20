package com.neopen.ddd.application.saga;

import com.neopen.ddd.application.dto.OrderCreateCommand;
import com.neopen.ddd.application.dto.OrderCreatedResponse;
import com.neopen.ddd.domain.event.DomainEventPublisher;
import com.neopen.ddd.domain.model.Money;
import com.neopen.ddd.domain.model.Order;
import com.neopen.ddd.domain.model.OrderPricing;
import com.neopen.ddd.domain.model.OrderStatusHistory;
import com.neopen.ddd.domain.model.ProductItem;
import com.neopen.ddd.domain.repository.OrderRepository;
import com.neopen.ddd.domain.repository.OrderStatusHistoryRepository;
import com.neopen.ddd.domain.service.OrderPricingService;
import com.neopen.ddd.infrastructure.idempotency.IdempotencyService;
import com.neopen.ddd.infrastructure.integration.inventory.acl.InventoryServiceAcl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 创建订单的 Saga 编排器 (Saga Orchestrator)。
 *
 * <p>为什么订单创建需要 Saga：一次下单同时涉及本地数据库与远端库存服务，
 * 二者无法共享事务。这里用“正向步骤 + 反向补偿”的编排式 Saga 显式表达整个过程，
 * 并把每一步的执行情况写入 {@code tab_saga_instance} / {@code tab_saga_step}。</p>
 *
 * <p>步骤定义：</p>
 * <ol>
 *   <li>{@code PRICE}：计算优惠与应付金额（本地，失败无需补偿）；</li>
 *   <li>{@code RESERVE_STOCK}：逐项调用库存服务生成预占单（外部，补偿为释放预占）；</li>
 *   <li>{@code PERSIST_ORDER}：保存订单、状态历史并写入 Outbox（本地事务）。</li>
 * </ol>
 *
 * <p>可靠性设计：</p>
 * <ul>
 *   <li>重试：库存预占失败按 {@code app.saga.reserve-max-attempts} 重试，对外部调用做有限重试；</li>
 *   <li>补偿：任何步骤失败都会释放已创建的预占单，避免库存悬挂；</li>
 *   <li>故障注入：{@code app.saga.fault-step} 可指定步骤名，在演示/测试中模拟失败；</li>
 *   <li>超时：{@link SagaTimeoutJob} 负责发现长时间未完成的实例。</li>
 * </ul>
 *
 * <p>事务说明：方法本身参与调用方事务，订单与事件随主事务提交或回滚；
 * Saga 轨迹由 {@link SagaJournal} 以独立事务写入，不受主事务回滚影响。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderCreationSaga {

    /** Saga 类型标识。 */
    public static final String SAGA_TYPE = "ORDER_CREATE";

    /** 步骤：定价。 */
    public static final String STEP_PRICE = "PRICE";

    /** 步骤：预占库存。 */
    public static final String STEP_RESERVE_STOCK = "RESERVE_STOCK";

    /** 步骤：持久化订单与事件。 */
    public static final String STEP_PERSIST_ORDER = "PERSIST_ORDER";

    private final SagaJournal journal;
    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final OrderPricingService pricingService;
    private final InventoryServiceAcl inventoryAcl;
    private final DomainEventPublisher domainEventPublisher;
    private final IdempotencyService idempotencyService;

    /** 故障注入的步骤名；为空表示不注入故障。 */
    @Value("${app.saga.fault-step:}")
    private String faultStep;

    /** 库存预占的最大尝试次数。 */
    @Value("${app.saga.reserve-max-attempts:2}")
    private int reserveMaxAttempts;

    /**
     * 执行创建订单 Saga。
     *
     * @param command        创建订单命令
     * @param idempotencyKey 幂等键，可空
     * @return 创建结果
     * @throws IllegalStateException 库存预占失败或步骤被故障注入中断
     */
    @Transactional
    public OrderCreatedResponse create(OrderCreateCommand command, String idempotencyKey) {
        // 1. 定价与聚合构建属于本地内存操作，失败不会产生外部副作用。
        List<ProductItem> items = command.getItems().stream()
                .map(item -> ProductItem.create(
                        item.getProductId(),
                        item.getProductName(),
                        item.getQuantity(),
                        new Money(item.getPrice(), Money.DEFAULT_CURRENCY)))
                .toList();
        OrderPricing pricing = pricingService.price(items);
        Order order = Order.create(command.getCustomerId(), items, pricing);

        // Saga 在订单号生成后开启，业务键使用订单号，便于按单据反查轨迹。
        String sagaId = journal.begin(SAGA_TYPE, order.getOrderNum());
        String currentStep = STEP_PRICE;
        try {
            journal.startStep(sagaId, STEP_PRICE);
            injectFailureIfNeeded(STEP_PRICE);
            journal.succeedStep(sagaId, STEP_PRICE);

            // 2. 预占库存：外部副作用，失败需要补偿已成功的部分。
            currentStep = STEP_RESERVE_STOCK;
            journal.startStep(sagaId, STEP_RESERVE_STOCK);
            injectFailureIfNeeded(STEP_RESERVE_STOCK);
            reserveStockWithRetry(order);
            order.markStockReserved();
            journal.succeedStep(sagaId, STEP_RESERVE_STOCK);

            // 3. 持久化：订单、历史与 Outbox 在同一本地事务内完成。
            currentStep = STEP_PERSIST_ORDER;
            journal.startStep(sagaId, STEP_PERSIST_ORDER);
            injectFailureIfNeeded(STEP_PERSIST_ORDER);
            // flush 立即暴露唯一约束与乐观锁冲突，避免异常延迟到事务提交阶段。
            orderRepository.saveAndFlush(order);
            historyRepository.save(OrderStatusHistory.forCreation(order, "创建订单"));
            domainEventPublisher.publishAll(order.pullDomainEvents());
            journal.succeedStep(sagaId, STEP_PERSIST_ORDER);

            OrderCreatedResponse response = OrderCreatedResponse.from(order);
            idempotencyService.record("ORDER_CREATE", idempotencyKey, response);
            journal.complete(sagaId);
            return response;
        } catch (RuntimeException ex) {
            // 失败轨迹独立事务落库，随后尽力回滚已创建的库存预占。
            journal.failStep(sagaId, currentStep, ex.getMessage());
            journal.startCompensation(sagaId, ex.getMessage());
            compensate(order);
            journal.compensated(sagaId);
            throw ex;
        }
    }

    /**
     * 逐项预占库存，单次失败在限定次数内重试。
     *
     * @param order 待预占的订单聚合
     * @throws IllegalStateException 任一订单项在重试后仍无法预占
     */
    private void reserveStockWithRetry(Order order) {
        for (ProductItem item : order.getProductItems()) {
            String reservationId = null;
            for (int attempt = 1; attempt <= Math.max(reserveMaxAttempts, 1); attempt++) {
                reservationId = inventoryAcl.reserveStock(
                        item.getProductId(), item.getQuantity(), order.getOrderNum());
                if (reservationId != null && !reservationId.isBlank()) {
                    break;
                }
                log.warn("库存预占失败，准备重试: orderNum={}, productId={}, attempt={}/{}",
                        order.getOrderNum(), item.getProductId(), attempt, reserveMaxAttempts);
            }
            if (reservationId == null || reservationId.isBlank()) {
                throw new IllegalStateException(
                        "库存不足或库存服务不可用: productId=" + item.getProductId());
            }
            // 预占单号回写到订单项，供支付确认、取消释放与对账使用。
            item.assignReservation(reservationId);
        }
    }

    /**
     * 补偿：释放本次 Saga 已创建的全部库存预占。
     *
     * <p>补偿过程中单个释放失败只记录日志并标记 Saga 失败，不再抛出异常，
     * 以免覆盖原始业务异常；失败记录可由对账任务或人工处理。</p>
     *
     * @param order 正在创建的订单聚合
     */
    private void compensate(Order order) {
        List<String> failed = new ArrayList<>();
        for (ProductItem item : order.getProductItems()) {
            String reservationId = item.getReservationId();
            if (reservationId == null || reservationId.isBlank()) {
                continue;
            }
            try {
                if (!inventoryAcl.releaseStock(reservationId)) {
                    failed.add(reservationId);
                }
            } catch (RuntimeException ex) {
                log.error("Saga 补偿释放库存异常: reservationId={}", reservationId, ex);
                failed.add(reservationId);
            }
        }
        if (!failed.isEmpty()) {
            log.error("Saga 补偿未完全成功，需人工处理: orderNum={}, reservations={}",
                    order.getOrderNum(), failed);
        }
    }

    /**
     * 故障注入：当配置的步骤名与当前步骤一致时抛出异常，用于验证补偿链路。
     *
     * @param stepName 当前步骤名
     * @throws IllegalStateException 命中故障注入时抛出
     */
    private void injectFailureIfNeeded(String stepName) {
        if (stepName.equalsIgnoreCase(faultStep)) {
            throw new IllegalStateException("Saga 故障注入触发: " + stepName);
        }
    }
}
