package com.neopen.ddd.application.saga;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Saga 步骤记录：Saga 实例下每个正向步骤的执行明细。
 *
 * <p>用途：定位失败发生在哪一步、每一步尝试了几次、补偿是否成功。
 * 与 {@link SagaInstance} 通过 {@code saga_id} 关联，但不建立 JPA 关联关系，
 * 使步骤可以独立按 Saga 查询，也让两者都保持简单。</p>
 */
@Entity
@Table(
        name = "tab_saga_step",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_saga_step_saga_name",
                        columnNames = {"saga_id", "step_name"})
        },
        indexes = {
                @Index(name = "idx_saga_step_saga_id", columnList = "saga_id, id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SagaStep {

    /** 数据库主键，同时保证同一 Saga 内步骤的展示顺序。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属 Saga 的业务标识。 */
    @Column(name = "saga_id", nullable = false, length = 64)
    private String sagaId;

    /** 步骤名，如 PRICE、RESERVE_STOCK、PERSIST_ORDER。 */
    @Column(name = "step_name", nullable = false, length = 64)
    private String stepName;

    /** 当前状态。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private SagaStepStatus status;

    /** 尝试次数，重试时累加。 */
    @Column(name = "attempts", nullable = false)
    private int attempts;

    /** 失败原因或补偿原因。 */
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    /** 开始执行时间。 */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    /** 结束时间（成功、失败或补偿完成）。 */
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    /**
     * 登记一个待执行步骤。
     *
     * @param sagaId   Saga 标识
     * @param stepName 步骤名
     * @return 状态为 RUNNING 的步骤记录
     */
    public static SagaStep start(String sagaId, String stepName) {
        SagaStep step = new SagaStep();
        step.sagaId = sagaId;
        step.stepName = stepName;
        step.status = SagaStepStatus.RUNNING;
        step.attempts = 1;
        step.startedAt = LocalDateTime.now();
        return step;
    }

    /** 标记步骤执行成功。 */
    public void succeed() {
        this.status = SagaStepStatus.SUCCEEDED;
        this.errorMessage = null;
        this.finishedAt = LocalDateTime.now();
    }

    /**
     * 记录一次失败。尝试次数由 {@link #retry()} 在下次执行前累加，
     * 因此失败那一刻的 attempts 表示“已尝试次数”。
     *
     * @param error 失败原因
     */
    public void fail(String error) {
        this.status = SagaStepStatus.FAILED;
        this.errorMessage = truncate(error);
        this.finishedAt = LocalDateTime.now();
    }

    /**
     * 重新执行该步骤：累加尝试次数并回到 RUNNING 状态。
     */
    public void retry() {
        this.attempts++;
        this.status = SagaStepStatus.RUNNING;
        this.errorMessage = null;
        this.finishedAt = null;
        this.startedAt = LocalDateTime.now();
    }

    /** 标记该步骤已完成反向补偿。 */
    public void compensate() {
        this.status = SagaStepStatus.COMPENSATED;
        this.finishedAt = LocalDateTime.now();
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
