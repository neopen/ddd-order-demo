package com.neopen.ddd.application.saga;

/**
 * Saga 实例状态。
 *
 * <pre>
 *   RUNNING --全部步骤成功--> COMPLETED
 *   RUNNING --任一步骤失败--> COMPENSATING --补偿完成--> COMPENSATED
 *   RUNNING --超过预算时间--> TIMEOUT
 * </pre>
 */
public enum SagaStatus {
    /** 正在执行步骤。 */
    RUNNING,
    /** 所有步骤成功，业务已完成。 */
    COMPLETED,
    /** 失败后正在执行反向补偿。 */
    COMPENSATING,
    /** 补偿完成，外部副作用已尽力回滚。 */
    COMPENSATED,
    /** 补偿本身失败或无法补偿，需要人工介入。 */
    FAILED,
    /** 长时间停留在 RUNNING，被超时任务判定为异常。 */
    TIMEOUT
}
