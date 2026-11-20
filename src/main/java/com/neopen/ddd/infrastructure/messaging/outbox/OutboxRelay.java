package com.neopen.ddd.infrastructure.messaging.outbox;

import com.neopen.ddd.infrastructure.messaging.KafkaEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 发件箱中继 (Outbox Relay)：定时扫描 PENDING 事件并投递到 Kafka。
 *
 * <p>调度周期：由 Spring 定时任务驱动，默认每 3 秒执行一轮
 * （{@code app.outbox.relay-interval-ms}），每轮最多处理 20 条记录；
 * 设置 {@code app.outbox.relay-enabled=false} 可整体关闭中继。</p>
 *
 * <p>状态流转：投递成功标记 {@code PUBLISHED} 并记录发布时间；
 * 投递失败累加 attempts 并保持 {@code PENDING} 等待下一轮重试；
 * 当 attempts 达到 {@code app.outbox.max-attempts}（默认 5）时标记 {@code FAILED}，
 * 停止自动重试，等待人工处理。</p>
 *
 * <p>事务与并发：整轮扫描与状态写回处于同一事务，单条投递异常在内部捕获，
 * 不会回滚本轮已处理的记录。当前实现假设单实例运行；多实例部署时可能重复投递，
 * 消费端需按事件 ID 做幂等 (Idempotency) 去重。</p>
 */
@Component
@ConditionalOnProperty(
        prefix = "app.outbox",
        name = "relay-enabled",
        havingValue = "true",
        matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class OutboxRelay {

    /** 发件箱记录仓库，用于读取待投递批次并写回状态。 */
    private final OutboxEventRepository outboxRepository;

    /** Kafka 发送器；Kafka 关闭时会退化为仅记录日志，中继仍视作投递成功。 */
    private final KafkaEventPublisher kafkaEventPublisher;

    /** 最大重试次数，达到后事件置为 FAILED，默认 5，可通过配置覆盖。 */
    @Value("${app.outbox.max-attempts:5}")
    private int maxAttempts;

    /**
     * 执行一轮中继：读取一批 PENDING 事件并逐条投递。
     *
     * <p>单条事件投递失败只影响自身状态（累加重试次数），不会中断本轮其余事件；
     * 异常在方法内部被捕获，因此不会因投递失败而回滚整个事务。</p>
     */
    @Scheduled(fixedDelayString = "${app.outbox.relay-interval-ms:3000}")
    @Transactional
    public void relay() {
        // 按主键升序批量处理，限制单事务内的事件数量
        List<OutboxEvent> pending = outboxRepository
                .findTop20ByStatusOrderByIdAsc(OutboxStatus.PENDING);
        if (pending.isEmpty()) {
            return;
        }

        for (OutboxEvent event : pending) {
            try {
                kafkaEventPublisher.publish(event.getAggregateId(), event.getPayload());
                event.markPublished();
            } catch (Exception ex) {
                // 记录根因并累加重试次数，超过上限时实体内部会置为 FAILED
                event.markFailed(rootMessage(ex), maxAttempts);
                log.warn("Outbox 事件投递失败，eventId={}, attempts={}, error={}",
                        event.getEventId(), event.getAttempts(), rootMessage(ex));
            }
            // save 兼作新增与更新，确保状态变更在当前事务内落库
            outboxRepository.save(event);
        }
    }

    /**
     * 提取异常链最底层的原因，拼成便于排查的单行描述。
     *
     * @param ex 原始异常
     * @return 形如 {@code IllegalStateException: 具体原因} 的简短描述
     */
    private String rootMessage(Exception ex) {
        Throwable cause = ex;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }
}
