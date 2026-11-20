package com.neopen.ddd.domain.model;

import com.neopen.ddd.domain.event.DomainEvent;
import jakarta.persistence.Transient;

import java.util.ArrayList;
import java.util.List;

/**
 * 聚合根 (Aggregate Root) 基类：在聚合内部记录领域事件 (Domain Event)，
 * 由应用层在持久化成功后统一发布。
 * <p>领域对象本身不依赖消息中间件与事务管理器，保证领域层可独立测试：
 * 事件先暂存在内存列表，应用层持久化成功后调用 {@link #pullDomainEvents()} 取出，
 * 再交给事件发布器写入 Outbox 并投递。</p>
 * <p>注意：聚合根实例不是线程安全的，应在同一事务与线程内完成状态修改和事件拉取，
 * 否则可能出现事件重复发布或与数据不一致。</p>
 */
public abstract class AggregateRoot {

    /**
     * 聚合内已发生但尚未发布的领域事件。
     * <p>标记 {@code @Transient} 表示该列表仅存在于内存、不映射数据库列；
     * 必须先保存聚合再拉取发布，避免“事件已发、数据回滚”。</p>
     */
    @Transient
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    /**
     * 登记一个领域事件，供应用层在聚合持久化后统一发布。
     * <p>只做内存登记而不立即发布，从而让事务成功与事件发布保持一致。</p>
     *
     * @param event 待登记的领域事件；为 {@code null} 时忽略，便于调用方简化分支
     */
    protected void registerEvent(DomainEvent event) {
        if (event != null) {
            domainEvents.add(event);
        }
    }

    /**
     * 取出并清空聚合内尚未发布的领域事件。
     * <p>返回的是快照副本，调用后内部列表被清空，避免同一事件被重复发布；
     * 应用层应在持久化成功后立即调用。</p>
     *
     * @return 自上次拉取后登记的领域事件快照，可能为空列表
     */
    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }
}
