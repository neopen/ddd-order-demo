package com.neopen.ddd.domain.statemachine;

import com.neopen.ddd.domain.event.DomainEventPublisher;
import com.neopen.ddd.domain.model.Order;
import com.neopen.ddd.domain.model.OrderEvent;
import com.neopen.ddd.domain.model.OrderStatus;
import com.neopen.ddd.domain.model.OrderStatusHistory;
import com.neopen.ddd.domain.repository.OrderRepository;
import com.neopen.ddd.domain.repository.OrderStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 订单状态迁移应用服务。
 * 负责事务、聚合根变更、持久化和领域事件发布；状态机生命周期由 Manager 负责。
 *
 * <p>职责分工：状态机只负责判断“当前状态 + 事件”是否合法以及目标状态是什么，
 * 订单聚合根负责维护 preStatus、updatedAt 等不变量并登记状态变更领域事件。</p>
 *
 * <p>关键协作：通过仓储加载订单，委托状态机管理器计算目标状态，
 * 再调用聚合根的 transitTo 完成迁移，最后写入历史记录并发布领域事件。</p>
 *
 * <p>事务/并发：整个迁移在单个数据库事务内完成；
 * saveAndFlush 触发 JPA @Version 乐观锁检查，并发冲突会整体回滚。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderStateMachineService {

    /** 订单聚合根仓储：负责加载订单与保存迁移结果。 */
    private final OrderRepository orderRepo;
    /** 状态流转历史仓储：为每次迁移留下可审计的记录。 */
    private final OrderStatusHistoryRepository historyRepository;
    /** 领域事件发布端口：实现基于 Outbox 的可靠投递。 */
    private final DomainEventPublisher domainEventPublisher;
    /** 状态机基础设施管理器：只做合法性判断与目标状态计算。 */
    private final OrderStateMachineManager stateMachineManager;

    /**
     * 对指定订单触发一次状态迁移。
     *
     * <p>执行顺序：状态机判定目标状态 -> 聚合根应用迁移并登记领域事件
     * -> saveAndFlush 校验乐观锁 -> 写入状态历史 -> 事件发布到 Outbox。</p>
     *
     * <p>该顺序保证：状态未成功落库时不会留下历史记录；
     * 历史与事件均与订单更新处于同一事务，要么全部成功，要么全部回滚。</p>
     *
     * @param orderId 订单 ID
     * @param event 触发的订单事件
     * @return true 表示订单状态已迁移；非法事件、状态冲突或并发冲突会抛出异常
     * @throws IllegalArgumentException 参数为空或订单不存在时抛出
     * @throws IllegalStateException 状态机拒绝事件或状态未发生变化时抛出
     * @throws org.springframework.dao.OptimisticLockingFailureException 并发修改冲突时抛出
     */
    @Transactional
    public boolean fire(Long orderId, OrderEvent event) {
        // 事件与订单 ID 缺一不可，避免后续仓储查询出现歧义
        if (orderId == null || event == null) {
            throw new IllegalArgumentException("订单ID和订单事件不能为空");
        }

        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在: " + orderId));
        OrderStatus from = order.getStatus();

        // Manager 只负责状态机执行；目标状态由状态机配置（包括退款失败的 Guard）决定。
        OrderStatus target = stateMachineManager.sendEvent(order, event);
        // 目标状态与当前状态相同说明事件没有实际迁移效果，继续落库会污染历史
        if (target == from) {
            throw new IllegalStateException(
                    "状态机接受了事件但订单状态未变化: orderId=" + orderId + ", event=" + event);
        }

        // 聚合根负责维护 preStatus、updatedAt 等领域不变量，并记录状态变更领域事件。
        order.transitTo(target, event);

        // 触发 JPA @Version 乐观锁检查；失败时事务回滚。
        orderRepo.saveAndFlush(order);

        // 历史记录在订单落库之后写入，与状态更新同事务，保证审计链完整。
        historyRepository.save(OrderStatusHistory.of(order, from, event, "订单状态流转"));

        // 领域事件写入 Outbox，由中继在事务提交后可靠投递；进程内监听器在提交后执行。
        domainEventPublisher.publishAll(order.pullDomainEvents());

        log.info("订单状态迁移成功，orderId={}, {} --{}--> {}", orderId, from, event, target);
        return true;
    }
}
