package com.neopen.ddd.application.saga;

/**
 * Saga 步骤状态。
 *
 * <pre>
 *   PENDING -> RUNNING -> SUCCEEDED
 *                     \-> FAILED --补偿成功--> COMPENSATED
 * </pre>
 */
public enum SagaStepStatus {
    /** 已登记但尚未执行。 */
    PENDING,
    /** 正在执行。 */
    RUNNING,
    /** 执行成功，无需补偿。 */
    SUCCEEDED,
    /** 执行失败，触发后续补偿。 */
    FAILED,
    /** 已完成反向补偿。 */
    COMPENSATED
}
