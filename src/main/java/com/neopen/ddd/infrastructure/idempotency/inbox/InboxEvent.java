package com.neopen.ddd.infrastructure.idempotency.inbox;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 收件箱事件 (Inbox Event)：消费端幂等去重记录。
 *
 * <p>为什么需要 Inbox：消息中间件通常提供“至少一次”投递，同一事件可能被重复消费；
 * 如果消费端直接写读模型，重复消息会造成计数翻倍、状态回退等问题。
 * 消费前先按 {@code consumer + eventId} 抢占一条 Inbox 记录，抢不到即视为重复消息跳过。</p>
 *
 * <p>唯一约束：{@code uk_inbox_consumer_event} 是最终防线，
 * 即使两个消费者实例同时通过“是否存在”检查，也只有一方能插入成功。</p>
 */
@Entity
@Table(
        name = "tab_inbox_event",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_inbox_consumer_event",
                        columnNames = {"consumer", "event_id"})
        },
        indexes = {
                @Index(name = "idx_inbox_event_id", columnList = "event_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InboxEvent {

    /** 数据库主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 消费者名称，不同消费逻辑使用不同命名空间，互不影响去重。 */
    @Column(name = "consumer", nullable = false, length = 64)
    private String consumer;

    /** 事件唯一 ID，来自集成事件信封。 */
    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    /** 事件类型，便于排查“哪些消息被消费过”。 */
    @Column(name = "event_type", length = 64)
    private String eventType;

    /** 接收时间。 */
    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    /** 处理完成时间，为空表示已接收但处理尚未确认。 */
    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    /**
     * 创建一条“已接收”的消费记录。
     *
     * @param consumer  消费者名称
     * @param eventId   事件 ID
     * @param eventType 事件类型
     * @return 待持久化的收件箱记录
     */
    public static InboxEvent received(String consumer, String eventId, String eventType) {
        InboxEvent event = new InboxEvent();
        event.consumer = consumer;
        event.eventId = eventId;
        event.eventType = eventType;
        event.receivedAt = LocalDateTime.now();
        return event;
    }

    /** 标记处理完成。 */
    public void markProcessed() {
        this.processedAt = LocalDateTime.now();
    }
}
