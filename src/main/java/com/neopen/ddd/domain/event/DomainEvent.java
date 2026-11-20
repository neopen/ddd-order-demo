package com.neopen.ddd.domain.event;

import java.io.Serializable;
import java.time.Instant;

/**
 * 领域事件 (Domain Event) 的顶层契约。
 * <p>领域事件表达“领域内已经发生的事实”，是聚合根 (Aggregate Root) 与外部世界解耦的
 * 主要手段：聚合只负责记录事件，应用层负责在事务内持久化事件（Outbox），基础设施层
 * 负责在事务提交后对外发布。</p>
 *
 * <p>设计约束：</p>
 * <ul>
 *   <li>继承 {@link Serializable}，便于写入 Outbox 表或跨进程传输。</li>
 *   <li>事件必须不可变：它描述的是过去，任何重试/重放都不能改变其内容。</li>
 *   <li>事件自带 ID 与发生时间，使消费端具备幂等去重与延迟度量的依据。</li>
 * </ul>
 *
 * <p>协作对象：发布端口为 {@link DomainEventPublisher}，进程内消费者为
 * {@code OrderEventListeners}。</p>
 */
public interface DomainEvent extends Serializable {

    /**
     * 事件的全局唯一标识。
     * <p>消费端以该值做幂等去重；同一事件在重试、转发、Outbox 重放过程中必须保持不变，
     * 否则消费端会重复处理。</p>
     *
     * @return 事件 ID，通常为 UUID 字符串
     */
    String getEventId();

    /**
     * 事件类型，默认取实现类的简单类名。
     * <p>作为消息路由键（routing key）与消费端反序列化的依据；重命名实现类会改变该值，
     * 需要同步调整下游订阅，因此不建议随意改名。</p>
     *
     * @return 事件类型名称，例如 {@code OrderCreatedEvent}
     */
    default String eventType() {
        return getClass().getSimpleName();
    }

    /**
     * 事件契约版本号，默认 {@code 1}。
     * <p>跨服务发布时写入集成事件信封，消费端可据此兼容多版本事件；
     * 字段含义发生不兼容变化时递增，而不是直接修改已发布的历史版本。</p>
     *
     * @return 事件版本号，从 1 开始
     */
    default int version() {
        return 1;
    }

    /**
     * 事件发生时间。
     * <p>实现类通常以 {@code occurredAt} 字段承载该值；默认方法保证集成事件信封
     * 总能取得时间戳，用于消费端排序与延迟度量。</p>
     *
     * @return 事件发生时间
     */
    default Instant occurredAt() {
        return Instant.now();
    }

    /**
     * 事件所属的聚合类型，默认值为兜底实现。
     * <p>具体事件应覆盖此方法返回真实聚合名（如 {@code Order}），以便按聚合维度
     * 分区消费或重放。</p>
     *
     * @return 聚合类型名称
     */
    default String aggregateType() {
        return "DomainEvent";
    }

    /**
     * 事件对应的聚合标识，默认退化为事件 ID。
     * <p>真实业务事件必须覆盖此方法返回聚合根标识（订单号、订单主键等），
     * 因为只有同一聚合的事件才需要保证顺序，而事件 ID 无法提供这种聚合内有序性。</p>
     *
     * @return 聚合标识
     */
    default String aggregateId() {
        return getEventId();
    }
}
