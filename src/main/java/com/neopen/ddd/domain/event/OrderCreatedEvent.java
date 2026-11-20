package com.neopen.ddd.domain.event;

import com.neopen.ddd.domain.model.Money;

import java.time.Instant;
import java.util.UUID;

/**
 * 订单创建事件 (Order Created Event)。
 * <p>由订单聚合根在创建时记录，应用层在订单保存成功后将其写入 Outbox 并对外发布。
 * 消费方据此发起积分发放、风控审核、消息通知等跨聚合副作用。</p>
 *
 * <p>字段语义：</p>
 * <ul>
 *   <li>{@code orderNum}：订单号，同时作为聚合标识，也是消费端幂等去重的业务键。</li>
 *   <li>{@code customerId}：下单客户标识，用于按用户维度统计与处理。</li>
 *   <li>{@code payableAmount}：应付金额，保留币种，避免消费端自行假设币种。</li>
 *   <li>{@code occurredAt}：事件在服务端产生的时刻，用于排序与计算处理延迟。</li>
 *   <li>{@code id}：事件唯一 ID，创建时生成，重试/转发期间保持不变，用于幂等与追踪。</li>
 * </ul>
 *
 * <p>record 的字段在构造后不可变，符合领域事件“描述已发生事实”的语义。</p>
 */
public record OrderCreatedEvent(
        String orderNum,
        Long customerId,
        Money payableAmount,
        Instant occurredAt,
        String id) implements DomainEvent {

    /**
     * 便捷构造器：自动填充事件发生时间与事件 ID。
     * <p>业务调用方只关心订单事实，时间与 ID 属于技术细节，由构造器统一生成，
     * 保证“一次事件一个 ID”，从而在重试时仍能保持幂等。</p>
     *
     * @param orderNum      订单号，同时作为聚合标识
     * @param customerId    下单客户标识
     * @param payableAmount 应付金额（含币种）
     */
    public OrderCreatedEvent(String orderNum, Long customerId, Money payableAmount) {
        this(orderNum, customerId, payableAmount, Instant.now(), UUID.randomUUID().toString());
    }

    /**
     * @return 事件唯一 ID，供消费端幂等去重
     */
    @Override
    public String getEventId() {
        return id;
    }

    /**
     * @return 聚合类型，固定为 {@code Order}
     */
    @Override
    public String aggregateType() {
        return "Order";
    }

    /**
     * @return 聚合标识，取订单号以保证同一订单的事件在消费端可有序处理
     */
    @Override
    public String aggregateId() {
        return orderNum;
    }
}
