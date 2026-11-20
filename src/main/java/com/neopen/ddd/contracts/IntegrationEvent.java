package com.neopen.ddd.contracts;

import com.fasterxml.jackson.databind.JsonNode;
import com.neopen.ddd.domain.event.DomainEvent;

/**
 * 集成事件信封 (Integration Event Envelope)：跨服务发布的事件契约。
 *
 * <p>为什么需要信封：领域事件是服务内部的模型，字段可以随重构自由演进；
 * 而跨服务消息一旦发布就形成契约，必须有稳定的元数据。信封把“稳定元数据”
 * 与“业务载荷”分开：元数据用于路由、去重、兼容与追踪，载荷才是各服务自己的领域字段。</p>
 *
 * <p>字段语义：</p>
 * <ul>
 *   <li>{@code eventId}：全局唯一事件 ID，消费端据此做幂等去重 (Idempotency)；</li>
 *   <li>{@code eventType}：事件类型，用于选择反序列化目标与路由；</li>
 *   <li>{@code version}：契约版本号，消费端可据此兼容新旧版本；</li>
 *   <li>{@code aggregateType}/{@code aggregateId}：聚合信息，同一聚合的消息应保持顺序；</li>
 *   <li>{@code occurredAt}：ISO-8601 时间字符串，避免不同语言的日期序列化差异；</li>
 *   <li>{@code payload}：业务载荷，保持为 JSON 树，消费端按需转换。</li>
 * </ul>
 *
 * <p>该类属于“发布语言 (Published Language)”，位于独立的 {@code contracts} 包，
 * 不依赖订单或库存的领域模型，可被多个服务共同引用。</p>
 *
 * @param eventId       事件唯一 ID
 * @param eventType     事件类型
 * @param version       事件契约版本
 * @param aggregateType 聚合类型
 * @param aggregateId   聚合标识
 * @param occurredAt    事件发生时间（ISO-8601）
 * @param payload       业务载荷
 */
public record IntegrationEvent(
        String eventId,
        String eventType,
        int version,
        String aggregateType,
        String aggregateId,
        String occurredAt,
        JsonNode payload) {

    /**
     * 由领域事件与已序列化的载荷构造信封。
     *
     * @param event   领域事件，提供元数据
     * @param payload 事件载荷的 JSON 树
     * @return 集成事件信封
     */
    public static IntegrationEvent of(DomainEvent event, JsonNode payload) {
        return new IntegrationEvent(
                event.getEventId(),
                event.eventType(),
                event.version(),
                event.aggregateType(),
                event.aggregateId(),
                event.occurredAt().toString(),
                payload);
    }
}
