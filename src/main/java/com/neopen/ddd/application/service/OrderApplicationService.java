package com.neopen.ddd.application.service;

import com.neopen.ddd.application.dto.*;
import com.neopen.ddd.domain.event.DomainEventPublisher;
import com.neopen.ddd.domain.model.*;
import com.neopen.ddd.domain.repository.OrderRepository;
import com.neopen.ddd.domain.repository.OrderStatusHistoryRepository;
import com.neopen.ddd.domain.service.OrderPricingService;
import com.neopen.ddd.domain.statemachine.OrderStateMachineService;
import com.neopen.ddd.infrastructure.idempotency.IdempotencyService;
import com.neopen.ddd.infrastructure.integration.inventory.acl.InventoryServiceAcl;
import com.neopen.ddd.infrastructure.integration.payment.acl.PaymentServiceAcl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 订单命令服务（CQRS 的 Command 侧）：编排用例、事务边界、库存/支付防腐层调用与补偿。
 * <p>分层职责：属于应用服务 (Application Service)，承载命令查询职责分离 (CQRS) 的写侧。
 * 只负责用例编排与事务划定，业务规则委托给聚合根、领域服务和状态机，不在此重复实现。</p>
 * <p>事务边界：本类 public 方法均由声明式事务包裹。订单、状态历史与发件箱 (Outbox)
 * 的本地写入在同一事务内提交；库存与支付属于外部服务，不参与本地事务，
 * 只能通过“先调用、失败后反向补偿”达到最终一致。</p>
 * <p>关键协作对象：{@link OrderRepository}/{@link OrderStatusHistoryRepository}
 * 负责持久化，{@link OrderPricingService} 负责定价，{@link InventoryServiceAcl} 与
 * {@link PaymentServiceAcl} 隔离外部协议，{@link OrderStateMachineService} 执行状态迁移，
 * {@link DomainEventPublisher} 投递领域事件 (Domain Event)，{@link IdempotencyService}
 * 提供接口幂等 (Idempotency) 支持。</p>
 * <p>并发与异常：聚合使用 JPA 乐观锁，并发冲突会抛出乐观锁异常并映射为 HTTP 409；
 * 状态非法、库存不足、支付失败等以运行时异常上抛，由全局异常处理器统一转换。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderApplicationService {

    /** 创建订单用例的幂等作用域 (Idempotency scope)，用于隔离不同接口的幂等键空间。 */
    private static final String IDEM_CREATE = "ORDER_CREATE";

    /** 支付用例的幂等作用域，与创建订单的幂等键互不影响。 */
    private static final String IDEM_PAY = "ORDER_PAY";

    /** 订单聚合仓储，保存时触发唯一约束与乐观锁 (Optimistic Lock) 校验。 */
    private final OrderRepository orderRepository;

    /** 状态流转历史仓储，记录每次合法状态迁移，用于审计与排障。 */
    private final OrderStatusHistoryRepository historyRepository;

    /** 库存服务防腐层 (Anti-Corruption Layer, ACL)，把外部调用结果转换为布尔值。 */
    private final InventoryServiceAcl inventoryAcl;

    /** 支付服务防腐层，封装支付与撤销支付调用，屏蔽渠道差异。 */
    private final PaymentServiceAcl paymentServiceAcl;

    /** 订单状态机应用服务，校验迁移合法性并负责落库历史与领域事件。 */
    private final OrderStateMachineService orderStateMachineService;

    /** 定价领域服务，按可用优惠策略计算原始金额、优惠金额与应付金额。 */
    private final OrderPricingService orderPricingService;

    /** 领域事件发布端口，具体实现把事件写入发件箱 (Outbox) 后异步投递。 */
    private final DomainEventPublisher domainEventPublisher;

    /** 幂等服务，按作用域与幂等键缓存首次执行结果，供重复请求回放。 */
    private final IdempotencyService idempotencyService;

    /**
     * 创建订单。
     * <p>固定执行顺序：幂等检查 -> 定价 -> 预占库存 -> 保存订单/历史/Outbox。</p>
     * <p>步骤说明：</p>
     * <ol>
     *   <li>幂等检查：以 {@code ORDER_CREATE} 为作用域查询幂等键，命中则直接回放首次结果；</li>
     *   <li>定价：命令项转换为领域值对象后，交由定价领域服务选出最优优惠策略；</li>
     *   <li>预占库存：逐项调用库存防腐层，任一项失败立即终止并进入补偿；</li>
     *   <li>持久化：保存订单并 flush 暴露唯一约束与乐观锁冲突，写入创建历史，
     *       再由发件箱 (Outbox) 持久化领域事件。</li>
     * </ol>
     * <p>补偿边界：库存预占与本地数据库不在同一事务中。try 块内任一步骤抛出运行时异常时，
     * 仅对“已成功预占”的订单项执行反向释放，随后原样抛出异常；本地写入随事务回滚。
     * 释放库存自身失败只会被记录，不会再次补偿或重试。生产环境还需要预占超时、对账
     * 和 Saga 日志来覆盖进程崩溃等极端场景。</p>
     *
     * @param command        创建订单命令，包含客户标识与订单项，已通过参数校验
     * @param idempotencyKey 幂等键，来自请求头 Idempotency-Key；为空时不做幂等控制
     * @return 创建结果；重复请求返回首次执行时缓存的结果
     * @throws IllegalArgumentException 客户或订单项不合法、定价结果与订单项金额不一致
     * @throws IllegalStateException    库存不足或库存服务不可用
     */
    @Transactional
    public OrderCreatedResponse createOrder(OrderCreateCommand command, String idempotencyKey) {
        // 幂等检查必须先于任何写操作，命中时直接回放首次结果。
        Optional<OrderCreatedResponse> replayed = idempotencyService
                .find(IDEM_CREATE, idempotencyKey, OrderCreatedResponse.class);
        if (replayed.isPresent()) {
            log.info("创建订单请求命中幂等记录，直接返回首次结果");
            return replayed.get();
        }

        // 命令 DTO 转为领域值对象；币种不由前端传入，统一使用默认币种。
        List<ProductItem> items = command.getItems().stream()
                .map(item -> ProductItem.create(
                        item.getProductId(),
                        item.getProductName(),
                        item.getQuantity(),
                        new Money(item.getPrice(), Money.DEFAULT_CURRENCY)))
                .toList();

        // 定价结果必须与订单项汇总金额一致，聚合创建时会再次校验。
        OrderPricing pricing = orderPricingService.price(items);
        Order order = Order.create(command.getCustomerId(), items, pricing);

        List<ProductItem> reservedItems = new ArrayList<>();
        try {
            // 逐项预占并记录成功项，作为异常时反向补偿的唯一依据。
            for (ProductItem item : order.getProductItems()) {
                if (!inventoryAcl.reserveStock(item.getProductId(), item.getQuantity())) {
                    throw new IllegalStateException(
                            "库存不足或库存服务不可用: productId=" + item.getProductId());
                }
                reservedItems.add(item);
            }
            order.markStockReserved();

            // flush 立即触发唯一约束与乐观锁校验，避免异常延迟到事务提交阶段。
            orderRepository.saveAndFlush(order);
            historyRepository.save(OrderStatusHistory.forCreation(order, "创建订单"));
            // 领域事件写入发件箱 (Outbox)，与本地事务同生共死，由中继异步投递。
            domainEventPublisher.publishAll(order.pullDomainEvents());

            OrderCreatedResponse response = OrderCreatedResponse.from(order);
            idempotencyService.record(IDEM_CREATE, idempotencyKey, response);
            return response;
        } catch (RuntimeException ex) {
            // Saga 补偿：订单落库失败时释放已预占的库存。
            reservedItems.forEach(item ->
                    inventoryAcl.releaseStock(item.getProductId(), item.getQuantity()));
            throw ex;
        }
    }

    /**
     * 订单支付。已支付订单重复调用时直接返回成功结果，避免重复扣款。
     * <p>固定执行顺序：幂等 -> 已支付短路 -> 支付 ACL -> 记录支付信息 -> 状态机迁移。</p>
     * <p>先调用外部支付，成功后才写入本地支付记录，最终由状态机完成 CREATED -> PAID。
     * 状态机迁移失败会回滚本地写入，但外部资金已扣减，需要依靠对账或人工介入补偿。</p>
     *
     * @param orderId        订单标识
     * @param idempotencyKey 幂等键，来自请求头 Idempotency-Key；为空时不做幂等控制
     * @return 支付结果，包含订单号、最新状态与提示信息
     * @throws IllegalArgumentException 订单不存在
     * @throws IllegalStateException    状态不允许支付、支付渠道失败或状态机迁移失败
     */
    @Transactional
    public OrderOperationResult payOrder(Long orderId, String idempotencyKey) {
        Optional<OrderOperationResult> replayed = idempotencyService
                .find(IDEM_PAY, idempotencyKey, OrderOperationResult.class);
        if (replayed.isPresent()) {
            // 幂等命中：重复提交的支付请求直接回放首次结果。
            return replayed.get();
        }

        Order order = loadOrder(orderId);
        if (order.getStatus() == OrderStatus.PAID) {
            // 已支付视为成功，属于业务级幂等兜底，防止无幂等键时重复扣款。
            return recordPayResult(idempotencyKey, order, "订单已支付");
        }
        if (order.getStatus() != OrderStatus.CREATED) {
            // 其余状态一律拒绝，支付仅允许从待支付状态发起。
            throw new IllegalStateException("只有待支付订单才能支付，当前状态：" + order.getStatus());
        }

        // 支付防腐层返回 false 表示渠道拒绝或调用异常，此时不写本地支付记录。
        if (!paymentServiceAcl.processPayment(order)) {
            throw new IllegalStateException("支付失败，请稍后重试");
        }

        // 记录渠道回执；该方法只维护聚合数据，不改变订单状态。
        order.recordPayment(new OrderPayment(
                "PAY-" + order.getOrderNum(),
                "TXN-" + order.getOrderNum(),
                "COMPLETED",
                order));

        // 状态机校验并落地 CREATED -> PAID，同时写入历史与领域事件。
        if (!orderStateMachineService.fire(orderId, OrderEvent.PAY)) {
            throw new IllegalStateException("支付失败，状态异常，订单号：" + order.getOrderNum());
        }

        return recordPayResult(idempotencyKey, loadOrder(orderId), "支付成功");
    }

    /**
     * 订单发货：由状态机把订单从已支付迁移为已发货，并写入状态历史与领域事件。
     *
     * @param orderId 订单标识
     * @return 发货结果，包含订单号、最新状态与提示信息
     * @throws IllegalArgumentException 订单不存在
     * @throws IllegalStateException    当前状态不允许发货
     */
    @Transactional
    public OrderOperationResult shipOrder(Long orderId) {
        return fireAndResult(orderId, OrderEvent.SHIP, "发货成功");
    }

    /**
     * 订单收货：记录买家签收，由状态机把已发货迁移为已签收。
     *
     * @param orderId 订单标识
     * @return 收货结果，包含订单号、最新状态与提示信息
     * @throws IllegalArgumentException 订单不存在
     * @throws IllegalStateException    当前状态不允许签收
     */
    @Transactional
    public OrderOperationResult receiveOrder(Long orderId) {
        return fireAndResult(orderId, OrderEvent.SIGN, "收货成功");
    }

    /**
     * 取消订单：已支付订单先撤销支付，待支付订单释放预占库存。
     * <p>外部补偿先于状态迁移执行；若状态迁移随后失败，本地事务回滚但外部支付或库存
     * 已经变更，需要依赖对账任务修正。</p>
     *
     * @param orderId 订单标识
     * @return 取消结果，包含订单号、最新状态与提示信息
     * @throws IllegalArgumentException 订单不存在
     * @throws IllegalStateException    撤销支付失败、释放库存失败或状态不允许取消
     */
    @Transactional
    public OrderOperationResult cancelOrder(Long orderId) {
        Order order = loadOrder(orderId);

        if (order.getStatus() == OrderStatus.PAID) {
            // 资金侧补偿必须在状态迁移前完成，失败则整体终止。
            if (!paymentServiceAcl.cancelPayment(order)) {
                throw new IllegalStateException("取消支付失败");
            }
        }
        if (order.getStatus() == OrderStatus.CREATED && order.isStockReserved()) {
            // 仅待支付且确实预占过库存的订单需要释放，避免误释放他人库存。
            releaseStock(order);
        }

        if (!orderStateMachineService.fire(orderId, OrderEvent.CANCEL)) {
            throw new IllegalStateException("取消失败，状态异常，订单号：" + order.getOrderNum());
        }
        return OrderOperationResult.of(loadOrder(orderId), "取消成功");
    }

    /**
     * 确认订单完成（签收后）：终态迁移，之后不再允许支付、发货或取消。
     *
     * @param orderId 订单标识
     * @return 完成结果，包含订单号、最新状态与提示信息
     * @throws IllegalArgumentException 订单不存在
     * @throws IllegalStateException    当前状态不允许完成
     */
    @Transactional
    public OrderOperationResult completeOrder(Long orderId) {
        return fireAndResult(orderId, OrderEvent.COMPLETE, "订单已完成");
    }

    /**
     * 申请退款。真正的退款渠道调用应在退款流程中异步处理并保证幂等。
     *
     * @param orderId 订单标识
     * @return 受理结果，订单进入退款中状态
     * @throws IllegalArgumentException 订单不存在
     * @throws IllegalStateException    当前状态不允许申请退款
     */
    @Transactional
    public OrderOperationResult refundOrder(Long orderId) {
        return fireAndResult(orderId, OrderEvent.REFUND, "退款申请已受理");
    }

    /**
     * 记录退款渠道成功回执。
     *
     * @param orderId 订单标识
     * @return 退款完成结果，订单进入已退款终态
     * @throws IllegalArgumentException 订单不存在
     * @throws IllegalStateException    当前状态不允许确认退款
     */
    @Transactional
    public OrderOperationResult refundSucceeded(Long orderId) {
        return fireAndResult(orderId, OrderEvent.REFUND_SUCCESS, "退款已完成");
    }

    /**
     * 记录退款渠道失败回执，并由状态机恢复退款前状态。
     *
     * @param orderId 订单标识
     * @return 退款失败结果，订单恢复到退款前状态
     * @throws IllegalArgumentException 订单不存在
     * @throws IllegalStateException    当前状态不允许回滚退款
     */
    @Transactional
    public OrderOperationResult refundFailed(Long orderId) {
        return fireAndResult(orderId, OrderEvent.REFUND_FAILED, "退款失败，订单已恢复原状态");
    }

    /**
     * 关闭待支付或已支付订单；已支付订单的资金撤销应先完成。
     * <p>与取消订单类似，外部支付撤销与库存释放先于状态迁移，失败即终止。</p>
     *
     * @param orderId 订单标识
     * @return 关闭结果，包含订单号、最新状态与提示信息
     * @throws IllegalArgumentException 订单不存在
     * @throws IllegalStateException    撤销支付失败、释放库存失败或状态不允许关闭
     */
    @Transactional
    public OrderOperationResult closeOrder(Long orderId) {
        Order order = loadOrder(orderId);
        if (order.getStatus() == OrderStatus.PAID) {
            // 已支付订单必须先撤销资金，避免关闭后出现账实不符。
            if (!paymentServiceAcl.cancelPayment(order)) {
                throw new IllegalStateException("撤销支付失败，不能关闭订单");
            }
        }
        if (order.getStatus() == OrderStatus.CREATED && order.isStockReserved()) {
            // 关闭待支付订单时归还预占库存。
            releaseStock(order);
        }

        if (!orderStateMachineService.fire(orderId, OrderEvent.CLOSE)) {
            throw new IllegalStateException("关闭失败，状态异常，订单号：" + order.getOrderNum());
        }
        return OrderOperationResult.of(loadOrder(orderId), "订单已关闭");
    }

    /**
     * 触发状态机迁移，并把迁移后重新加载的订单快照转换为操作结果。
     *
     * @param orderId 订单标识
     * @param event   触发迁移的订单事件
     * @param message 成功时返回给调用方的提示信息
     * @return 操作结果，状态取自迁移后的最新订单
     * @throws IllegalStateException 状态机拒绝迁移
     */
    private OrderOperationResult fireAndResult(Long orderId, OrderEvent event, String message) {
        if (!orderStateMachineService.fire(orderId, event)) {
            throw new IllegalStateException("订单状态迁移失败: orderId=" + orderId + ", event=" + event);
        }
        return OrderOperationResult.of(loadOrder(orderId), message);
    }

    /**
     * 构造支付结果并写入幂等记录，保证重复请求返回同一份结果。
     *
     * @param idempotencyKey 幂等键，为空时幂等服务不会落库
     * @param order          支付后的订单快照
     * @param message        返回给调用方的提示信息
     * @return 支付操作结果
     */
    private OrderOperationResult recordPayResult(String idempotencyKey, Order order, String message) {
        OrderOperationResult result = OrderOperationResult.of(order, message);
        idempotencyService.record(IDEM_PAY, idempotencyKey, result);
        return result;
    }

    /**
     * 按标识加载订单聚合。
     *
     * @param orderId 订单标识
     * @return 订单聚合根
     * @throws IllegalArgumentException 订单不存在
     */
    private Order loadOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在: " + orderId));
    }

    /**
     * 释放订单所有订单项的库存。失败时抛出异常，避免订单状态已变更但库存未释放。
     * <p>释放属于外部副作用，无法参与本地事务回滚，因此失败时直接中止当前用例。</p>
     *
     * @param order 需要释放库存的订单聚合根，完成后其预占标记被清除
     * @throws IllegalStateException 任一订单项释放库存失败
     */
    private void releaseStock(Order order) {
        for (ProductItem item : order.getProductItems()) {
            if (!inventoryAcl.releaseStock(item.getProductId(), item.getQuantity())) {
                throw new IllegalStateException(
                        "释放库存失败: productId=" + item.getProductId());
            }
        }
        order.releaseStockReservation();
    }
}
