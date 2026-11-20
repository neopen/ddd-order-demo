package com.neopen.ddd.application.dto;

import java.time.LocalDateTime;

/**
 * Saga 单个步骤的视图。
 *
 * @param stepName     步骤名
 * @param status       步骤状态
 * @param attempts     尝试次数
 * @param errorMessage 失败原因，成功时为 null
 * @param startedAt    开始时间
 * @param finishedAt   结束时间
 */
public record SagaStepView(
        String stepName,
        String status,
        int attempts,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime finishedAt) {
}
