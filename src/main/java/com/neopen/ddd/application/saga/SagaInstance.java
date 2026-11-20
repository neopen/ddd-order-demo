package com.neopen.ddd.application.saga;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Saga 实例 (Saga Instance)：一次跨服务业务流程的持久化执行记录。
 *
 * <p>为什么需要持久化：内存中的补偿列表在进程崩溃后全部丢失，外部已完成或已扣减的
 * 副作用将无人回收。把 Saga 状态落库后，可以通过定时任务发现长时间 RUNNING 的实例、
 * 追溯失败原因，并为人工补偿提供依据。</p>
 *
 * <p>与订单聚合的关系：Saga 是应用层的过程管理器 (Process Manager)，
 * 只保存业务键（订单号）而不持有订单对象，避免把编排逻辑侵入领域模型。</p>
 *
 * <p>事务语义：本实体由 {@link SagaJournal} 以独立事务 (REQUIRES_NEW) 写入，
 * 即使业务主事务回滚，Saga 轨迹也会保留。</p>
 */
@Entity
@Table(
        name = "tab_saga_instance",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_saga_instance_saga_id", columnNames = "saga_id")
        },
        indexes = {
                @Index(name = "idx_saga_instance_status_updated", columnList = "status, updated_at"),
                @Index(name = "idx_saga_instance_business_key", columnList = "business_key")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SagaInstance {

    /** 数据库主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Saga 业务唯一标识，对外暴露给查询接口与日志。 */
    @Column(name = "saga_id", nullable = false, length = 64)
    private String sagaId;

    /** Saga 类型，如 {@code ORDER_CREATE}，用于区分不同业务编排。 */
    @Column(name = "saga_type", nullable = false, length = 64)
    private String sagaType;

    /** 业务键，订单场景取订单号，用于按业务单据反查执行轨迹。 */
    @Column(name = "business_key", nullable = false, length = 64)
    private String businessKey;

    /** 当前状态。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private SagaStatus status;

    /** 当前或最后执行的步骤名，便于快速定位卡在哪一步。 */
    @Column(name = "current_step", length = 64)
    private String currentStep;

    /** 失败原因，成功时为 {@code null}。 */
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    /** 开始时间。 */
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    /** 结束时间（完成、补偿完成、失败或超时）。 */
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    /** 最后更新时间，超时任务据此判断实例是否长时间无进展。 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** 乐观锁版本，避免并发任务互相覆盖 Saga 状态。 */
    @Version
    private Long version;

    /**
     * 创建处于 RUNNING 状态的新实例。
     *
     * @param sagaType    流程类型
     * @param businessKey 业务键，通常为订单号
     * @return 尚未持久化的 Saga 实例
     */
    public static SagaInstance start(String sagaType, String businessKey) {
        SagaInstance instance = new SagaInstance();
        instance.sagaId = "SAGA-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        instance.sagaType = sagaType;
        instance.businessKey = businessKey;
        instance.status = SagaStatus.RUNNING;
        instance.startedAt = LocalDateTime.now();
        instance.updatedAt = instance.startedAt;
        return instance;
    }

    /**
     * 记录当前执行到的步骤。
     *
     * @param stepName 步骤名
     */
    public void moveTo(String stepName) {
        this.currentStep = stepName;
        touch();
    }

    /** 标记流程全部成功。 */
    public void complete() {
        this.status = SagaStatus.COMPLETED;
        this.finishedAt = LocalDateTime.now();
        touch();
    }

    /**
     * 进入补偿阶段。
     *
     * @param reason 触发补偿的原因
     */
    public void startCompensation(String reason) {
        this.status = SagaStatus.COMPENSATING;
        this.errorMessage = truncate(reason);
        touch();
    }

    /** 标记补偿完成：外部副作用已尽力回滚。 */
    public void compensate() {
        this.status = SagaStatus.COMPENSATED;
        this.finishedAt = LocalDateTime.now();
        touch();
    }

    /**
     * 标记补偿失败，需要人工介入。
     *
     * @param reason 失败原因
     */
    public void fail(String reason) {
        this.status = SagaStatus.FAILED;
        this.errorMessage = truncate(reason);
        this.finishedAt = LocalDateTime.now();
        touch();
    }

    /** 标记超时，通常在长时间无进展后由定时任务调用。 */
    public void timeout() {
        this.status = SagaStatus.TIMEOUT;
        this.errorMessage = "Saga 执行超时，未在预算时间内完成";
        this.finishedAt = LocalDateTime.now();
        touch();
    }

    /** 刷新更新时间。 */
    private void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 截断错误信息以适配列长度。
     *
     * @param error 原始错误信息
     * @return 不超过 1000 字符的信息
     */
    private String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() > 1000 ? error.substring(0, 1000) : error;
    }
}
