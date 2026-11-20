package com.neopen.ddd.infrastructure.idempotency.inbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 收件箱服务：为消息消费提供幂等去重能力。
 *
 * <p>实现要点：抢占记录使用独立事务 (REQUIRES_NEW)，这样唯一约束冲突只会回滚
 * 抢占动作本身，不会把调用方的业务事务标记为 rollback-only。
 * 冲突异常在事务模板之外捕获，返回 false 表示“该事件已被其他消费者处理”。</p>
 *
 * <p>与业务消费的关系：本服务只负责“记录已收到”，
 * 调用方在抢占成功后执行投影更新，再调用 {@link #markProcessed} 标记完成。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InboxService {

    private final InboxEventRepository inboxRepository;

    private final PlatformTransactionManager transactionManager;

    /**
     * 尝试抢占事件消费权。
     *
     * @param consumer  消费者名称，用于隔离不同消费逻辑的去重空间
     * @param eventId   事件唯一 ID
     * @param eventType 事件类型，仅用于记录
     * @return true 表示本次可以处理；false 表示重复消息
     */
    public boolean tryConsume(String consumer, String eventId, String eventType) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            Boolean created = template.execute(status -> {
                if (inboxRepository.existsByConsumerAndEventId(consumer, eventId)) {
                    return false;
                }
                inboxRepository.saveAndFlush(InboxEvent.received(consumer, eventId, eventType));
                return true;
            });
            return Boolean.TRUE.equals(created);
        } catch (DataIntegrityViolationException ex) {
            // 并发下两个实例同时通过存在性检查，唯一约束保证只有一方成功。
            log.info("收件箱已存在该事件，按重复消息跳过: consumer={}, eventId={}", consumer, eventId);
            return false;
        }
    }

    /**
     * 标记事件处理完成。
     *
     * @param consumer 消费者名称
     * @param eventId  事件 ID
     */
    @Transactional
    public void markProcessed(String consumer, String eventId) {
        inboxRepository.findByConsumerAndEventId(consumer, eventId)
                .ifPresent(InboxEvent::markProcessed);
    }
}
