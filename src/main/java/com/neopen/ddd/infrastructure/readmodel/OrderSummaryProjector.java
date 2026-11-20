package com.neopen.ddd.infrastructure.readmodel;

import com.fasterxml.jackson.databind.JsonNode;
import com.neopen.ddd.contracts.IntegrationEvent;
import com.neopen.ddd.domain.event.OrderCreatedEvent;
import com.neopen.ddd.domain.event.OrderStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 订单读模型投影器 (Projector)：把领域事件翻译为读模型更新。
 *
 * <p>两条输入通道：</p>
 * <ol>
 *   <li>进程内领域事件：{@link EventListener} 同步接收，与业务事务一起提交，读模型强一致；</li>
 *   <li>跨服务集成事件：{@link #applyIntegrationEvent} 由 Kafka 消费者调用，
 *       按信封中的 {@code eventType} 解析载荷，配合 Inbox 去重实现最终一致。</li>
 * </ol>
 *
 * <p>幂等设计：投影本身也做事件级去重（时间戳与事件 ID），
 * 即使 Inbox 记录因人工重放被清理，重复事件也不会让状态回退。</p>
 *
 * <p>失败处理：事件处理异常会向上抛出，由调用方决定重试；
 * 同步通道会随业务事务一起回滚，异步通道则依赖 Kafka 的重试与死信策略。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderSummaryProjector {

    private final OrderSummaryProjectionRepository projectionRepository;

    /**
     * 处理订单创建事件（本地同步通道）。
     *
     * @param event 订单创建事件
     */
    @EventListener
    @Transactional
    public void onOrderCreated(OrderCreatedEvent event) {
        OrderSummaryProjection projection = projectionRepository.findById(event.orderNum())
                .orElseGet(() -> OrderSummaryProjection.blank(event.orderNum(), null));
        projection.applyCreated(
                event.customerId(),
                event.payableAmount().getAmount(),
                event.payableAmount().getCurrency(),
                event.id(),
                event.occurredAt());
        projectionRepository.save(projection);
    }

    /**
     * 处理订单状态变更事件（本地同步通道）。
     *
     * @param event 状态变更事件
     */
    @EventListener
    @Transactional
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        OrderSummaryProjection projection = projectionRepository.findById(event.orderNum())
                .orElseGet(() -> OrderSummaryProjection.blank(event.orderNum(), event.orderId()));
        projection.applyStatus(event.orderId(), event.to().name(), event.id(), event.occurredAt());
        projectionRepository.save(projection);
    }

    /**
     * 处理跨服务集成事件（Kafka 通道）。
     *
     * <p>不解码为领域对象，而是直接读取 JSON 字段，避免消费端与生产端领域模型强耦合；
     * 未知事件类型只记录日志，保证新增事件不会打断既有消费者。</p>
     *
     * @param envelope 集成事件信封
     * @throws IllegalArgumentException 载荷缺少必要字段时抛出，触发消息重试
     */
    @Transactional
    public void applyIntegrationEvent(IntegrationEvent envelope) {
        JsonNode payload = envelope.payload();
        Instant occurredAt = parseInstant(envelope.occurredAt());
        switch (envelope.eventType()) {
            case "OrderCreatedEvent" -> {
                String orderNum = requireText(payload, "orderNum");
                JsonNode money = payload.path("payableAmount");
                OrderSummaryProjection projection = projectionRepository.findById(orderNum)
                        .orElseGet(() -> OrderSummaryProjection.blank(orderNum, null));
                projection.applyCreated(
                        payload.hasNonNull("customerId") ? payload.get("customerId").asLong() : null,
                        money.path("amount").decimalValue(),
                        money.path("currency").asText(),
                        envelope.eventId(),
                        occurredAt);
                projectionRepository.save(projection);
            }
            case "OrderStatusChangedEvent" -> {
                String orderNum = requireText(payload, "orderNum");
                String targetStatus = requireText(payload, "to");
                Long orderId = payload.hasNonNull("orderId") ? payload.get("orderId").asLong() : null;
                OrderSummaryProjection projection = projectionRepository.findById(orderNum)
                        .orElseGet(() -> OrderSummaryProjection.blank(orderNum, orderId));
                projection.applyStatus(orderId, targetStatus, envelope.eventId(), occurredAt);
                projectionRepository.save(projection);
            }
            default -> log.debug("忽略未订阅的事件类型: {}", envelope.eventType());
        }
    }

    /**
     * 读取必填文本字段。
     *
     * @param node  载荷节点
     * @param field 字段名
     * @return 字段值
     * @throws IllegalArgumentException 字段缺失或为空白时抛出
     */
    private String requireText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.asText().isBlank()) {
            throw new IllegalArgumentException("集成事件缺少必要字段: " + field);
        }
        return value.asText();
    }

    /**
     * 解析事件时间，缺失时退化为当前时间，避免因元数据问题丢弃业务事件。
     *
     * @param occurredAt ISO-8601 时间字符串
     * @return 事件时间
     */
    private Instant parseInstant(String occurredAt) {
        if (occurredAt == null || occurredAt.isBlank()) {
            return Instant.now();
        }
        return Instant.parse(occurredAt);
    }
}
