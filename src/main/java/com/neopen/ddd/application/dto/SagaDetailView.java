package com.neopen.ddd.application.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Saga 执行详情视图。
 *
 * @param sagaId       Saga 业务标识
 * @param sagaType     流程类型，如 ORDER_CREATE
 * @param businessKey  业务键，订单场景为订单号
 * @param status       Saga 状态
 * @param currentStep  当前或最后执行的步骤
 * @param errorMessage 失败原因，成功时为 null
 * @param startedAt    开始时间
 * @param finishedAt   结束时间，未结束时为 null
 * @param steps        步骤明细，按执行顺序排列
 */
public record SagaDetailView(
        String sagaId,
        String sagaType,
        String businessKey,
        String status,
        String currentStep,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        List<SagaStepView> steps) {
}
