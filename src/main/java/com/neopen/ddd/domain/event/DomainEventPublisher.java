package com.neopen.ddd.domain.event;

import java.util.List;

/**
 * 领域事件发布端口 (Port)。
 * <p>属于领域层/应用层的出站端口：上层只依赖此抽象，具体实现（Outbox 落库、消息队列投递）
 * 位于基础设施层，从而保持领域模型对基础设施的无感知（依赖倒置）。</p>
 *
 * <p>实现约定：调用方通常在业务事务内调用，把事件与业务变更一起原子写入 Outbox，
 * 再由中继在事务提交后异步投递，因此实现不应依赖调用方事务是否已提交，
 * 也不应以抛出异常的方式让业务回滚（事件落库失败属于基础设施故障）。</p>
 */
public interface DomainEventPublisher {

    /**
     * 批量发布领域事件。
     * <p>批量语义允许实现合并为一次数据库写入或一次网络往返；调用方需保证
     * 传入事件与当前业务变更处于同一事务边界，否则可能出现“业务成功但事件丢失”。</p>
     *
     * @param events 待发布事件列表；实现应容忍空列表，元素不可为 {@code null}
     */
    void publishAll(List<DomainEvent> events);
}
