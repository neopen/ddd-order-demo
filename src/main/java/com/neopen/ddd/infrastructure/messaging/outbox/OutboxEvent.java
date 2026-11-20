package com.neopen.ddd.infrastructure.messaging.outbox;

import com.neopen.ddd.domain.event.DomainEvent;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 事务性发件箱 (Transactional Outbox) 记录：领域事件与业务数据在同一数据库事务中写入，
 * 再由后台中继 {@link OutboxRelay} 异步投递到 Kafka，保证数据库与消息的最终一致。
 *
 * <p>对应表 {@code tab_outbox_event}，唯一约束 {@code uk_outbox_event_id} 约束
 * {@code event_id}，防止同一事件重复落库；索引 {@code idx_outbox_status_id}
 * 服务于中继按状态、主键顺序批量扫描。</p>
 *
 * <p>状态流转：{@code PENDING -> PUBLISHED}（投递成功）或
 * {@code PENDING -> FAILED}（重试次数达到上限），详见 {@link OutboxStatus}。</p>
 *
 * <p>线程安全：实体本身非线程安全，仅在单个事务内被创建与修改；
 * 无参构造器仅供 JPA 使用，业务代码应通过 {@link #create} 工厂方法创建实例。</p>
 */
@Entity
@Table(
        name = "tab_outbox_event",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_outbox_event_id", columnNames = "event_id")
        },
        indexes = {
                @Index(name = "idx_outbox_status_id", columnList = "status, id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    /** 数据库自增主键，仅用于排序与批量扫描，不承载业务语义。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 领域事件全局唯一 ID，受唯一约束保护，供消费端做幂等 (Idempotency) 去重。 */
    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    /** 产生事件的聚合根类型（如 Order），便于排查与按聚合维度归类。 */
    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    /** 聚合根业务标识，投递时作为 Kafka 消息 key，保证同一聚合的事件分区有序。 */
    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    /** 事件类型标识（如 OrderCreated），供消费端识别与路由。 */
    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    /** 事件序列化后的 JSON 报文，中继阶段原样投递，不做二次加工。 */
    @Lob
    @Column(name = "payload", nullable = false)
    private String payload;

    /** 投递状态：PENDING 待投递 / PUBLISHED 已投递 / FAILED 超过重试上限。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private OutboxStatus status;

    /** 已失败次数，每次投递失败自增；达到上限后状态转为 FAILED。 */
    @Column(name = "attempts", nullable = false)
    private int attempts;

    /** 最近一次失败原因，便于人工排查；列长度 1000，写入前会截断。 */
    @Column(name = "last_error", length = 1000)
    private String lastError;

    /** 记录创建时间，与业务数据同事务写入，可作为事件产生时间的参考。 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 成功投递到 Kafka 的时间；尚未投递时为 {@code null}。 */
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    /**
     * 创建一条待投递的发件箱记录。
     *
     * <p>仅完成对象装配，不落库、不发送消息；调用方需保证在业务事务内执行，
     * 使事件记录与业务数据一起提交或回滚。</p>
     *
     * @param event   领域事件，不能为 {@code null}
     * @param payload 事件序列化后的 JSON 报文，后续由中继原样投递
     * @return 状态为 {@link OutboxStatus#PENDING} 的新记录
     * @throws NullPointerException 当事件为 {@code null} 时抛出
     */
    public static OutboxEvent create(DomainEvent event, String payload) {
        Objects.requireNonNull(event, "领域事件不能为空");
        OutboxEvent record = new OutboxEvent();
        record.eventId = event.getEventId();
        record.aggregateType = event.aggregateType();
        record.aggregateId = event.aggregateId();
        record.eventType = event.eventType();
        record.payload = payload;
        record.status = OutboxStatus.PENDING;
        record.createdAt = LocalDateTime.now();
        return record;
    }

    /**
     * 标记事件已成功投递。
     * <p>同时记录发布时间并清除上一次的失败信息，使记录只反映最终成功状态。</p>
     */
    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
        this.lastError = null;
    }

    /**
     * 记录一次投递失败并累计重试次数。
     *
     * <p>未达到上限时状态保持 {@code PENDING}，等待下一轮中继重试；
     * 达到或超过上限时置为 {@code FAILED}，不再自动重试。</p>
     *
     * @param error       失败原因，超长会被截断以适配列长度
     * @param maxAttempts 最大重试次数，由配置 {@code app.outbox.max-attempts} 提供
     */
    public void markFailed(String error, int maxAttempts) {
        this.attempts++;
        this.lastError = truncate(error);
        // 达到重试上限后进入终态，需人工排查并重置状态
        this.status = this.attempts >= maxAttempts ? OutboxStatus.FAILED : OutboxStatus.PENDING;
    }

    /**
     * 截断错误信息以适配 {@code last_error} 列长度。
     *
     * @param error 原始错误信息，允许为 {@code null}
     * @return 不超过 1000 字符的错误信息；入参为 {@code null} 时返回 {@code null}
     */
    private String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() > 1000 ? error.substring(0, 1000) : error;
    }
}
