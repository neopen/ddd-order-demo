package com.neopen.ddd.infrastructure.messaging.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neopen.ddd.domain.event.DomainEvent;
import com.neopen.ddd.domain.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 领域事件发布实现（发件箱模式）。
 * <ol>
 *     <li>把事件写入 Outbox 表，与业务数据共享同一个数据库事务；</li>
 *     <li>同时发布进程内 Spring 事件，供监听器在事务提交后执行本地副作用。</li>
 * </ol>
 *
 * <p>可靠性语义：事件记录与业务聚合的写入要么一起提交、要么一起回滚，
 * 因此业务事务一旦成功，事件必然已持久化；Kafka 投递的最终一致由
 * {@link OutboxRelay} 异步完成，发布方无需依赖消息中间件的实时可用性。</p>
 *
 * <p>线程安全：本类无可变状态，可被多个业务请求并发调用；
 * 每次调用只操作当前事务上下文中的实体，不共享数据。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxDomainEventPublisher implements DomainEventPublisher {

    /** 发件箱记录仓库，负责事件持久化，与业务数据共用同一事务。 */
    private final OutboxEventRepository outboxRepository;

    /** 进程内事件发布器，用于触发本地监听（如刷新缓存、写审计日志）。 */
    private final ApplicationEventPublisher localPublisher;

    /** JSON 序列化器，把领域事件转换为可持久化的报文。 */
    private final ObjectMapper objectMapper;

    /**
     * 批量发布领域事件：逐条写入发件箱并发布进程内事件。
     *
     * <p>任一事件序列化失败都会抛出异常，使当前业务事务整体回滚，
     * 避免出现“业务已提交但事件丢失”的不一致；因此本方法必须在业务事务内调用。</p>
     *
     * @param events 待发布的领域事件集合，为 {@code null} 或空时直接返回
     * @throws IllegalStateException 事件无法序列化为 JSON 时抛出，用于触发事务回滚
     */
    @Override
    public void publishAll(List<DomainEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }

        // 单条事件的落库与本地发布保持在同一事务中，失败即整体回滚
        for (DomainEvent event : events) {
            String payload;
            try {
                payload = objectMapper.writeValueAsString(event);
            } catch (JsonProcessingException ex) {
                // 序列化失败直接中断，让业务事务回滚，保证事件不丢失
                throw new IllegalStateException("领域事件序列化失败: " + event.eventType(), ex);
            }

            outboxRepository.save(OutboxEvent.create(event, payload));
            localPublisher.publishEvent(event);
            log.debug("领域事件已写入 Outbox: type={}, aggregateId={}",
                    event.eventType(), event.aggregateId());
        }
    }
}
