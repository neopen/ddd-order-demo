package com.neopen.ddd.application.saga;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saga 轨迹日志：以独立事务持久化编排过程。
 *
 * <p>为什么必须独立事务：订单创建失败时业务主事务会回滚（订单、历史、Outbox 全部撤销），
 * 如果 Saga 轨迹与业务共用事务，失败证据也会一并消失，后续既无法对账也无法人工补偿。
 * 因此这里所有写方法都声明 {@link Propagation#REQUIRES_NEW}，业务事务回滚不影响轨迹落库。</p>
 *
 * <p>调用约束：本类方法由 {@link OrderCreationSaga} 等编排器调用；
 * 由于 REQUIRES_NEW 会挂起外层事务，调用频率应保持在“每步骤一次”的量级，
 * 不要在高频循环中逐条写入。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SagaJournal {

    /** Saga 实例仓储。 */
    private final SagaInstanceRepository instanceRepository;

    /** Saga 步骤仓储。 */
    private final SagaStepRepository stepRepository;

    /**
     * 开启一个新的 Saga 实例。
     *
     * @param sagaType    流程类型
     * @param businessKey 业务键，通常为订单号
     * @return Saga 业务标识
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String begin(String sagaType, String businessKey) {
        SagaInstance instance = instanceRepository.save(SagaInstance.start(sagaType, businessKey));
        log.info("Saga 已开启: sagaId={}, type={}, businessKey={}",
                instance.getSagaId(), sagaType, businessKey);
        return instance.getSagaId();
    }

    /**
     * 登记步骤开始；若该步骤已存在则累加尝试次数并重新执行。
     *
     * @param sagaId   Saga 标识
     * @param stepName 步骤名
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void startStep(String sagaId, String stepName) {
        instanceRepository.findBySagaId(sagaId).ifPresent(instance -> instance.moveTo(stepName));
        SagaStep step = stepRepository.findBySagaIdAndStepName(sagaId, stepName)
                .orElseGet(() -> SagaStep.start(sagaId, stepName));
        if (step.getStatus() == SagaStepStatus.FAILED) {
            step.retry();
        }
        stepRepository.save(step);
    }

    /**
     * 标记步骤成功。
     *
     * @param sagaId   Saga 标识
     * @param stepName 步骤名
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void succeedStep(String sagaId, String stepName) {
        stepRepository.findBySagaIdAndStepName(sagaId, stepName)
                .ifPresent(SagaStep::succeed);
    }

    /**
     * 标记步骤失败，记录失败原因供排查。
     *
     * @param sagaId   Saga 标识
     * @param stepName 步骤名
     * @param error    失败原因
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failStep(String sagaId, String stepName, String error) {
        stepRepository.findBySagaIdAndStepName(sagaId, stepName)
                .ifPresent(step -> step.fail(error));
    }

    /**
     * 进入补偿阶段。
     *
     * @param sagaId Saga 标识
     * @param reason 触发补偿的原因
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void startCompensation(String sagaId, String reason) {
        instanceRepository.findBySagaId(sagaId)
                .ifPresent(instance -> instance.startCompensation(reason));
    }

    /**
     * 标记某个已执行步骤完成反向补偿。
     *
     * @param sagaId   Saga 标识
     * @param stepName 步骤名
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void compensateStep(String sagaId, String stepName) {
        stepRepository.findBySagaIdAndStepName(sagaId, stepName)
                .ifPresent(SagaStep::compensate);
    }

    /**
     * 标记整个 Saga 补偿完成。
     *
     * @param sagaId Saga 标识
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void compensated(String sagaId) {
        instanceRepository.findBySagaId(sagaId).ifPresent(SagaInstance::compensate);
        log.info("Saga 补偿完成: sagaId={}", sagaId);
    }

    /**
     * 标记整个 Saga 执行成功。
     *
     * @param sagaId Saga 标识
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(String sagaId) {
        instanceRepository.findBySagaId(sagaId).ifPresent(SagaInstance::complete);
        log.info("Saga 执行完成: sagaId={}", sagaId);
    }

    /**
     * 标记 Saga 失败（通常表示补偿也失败了）。
     *
     * @param sagaId Saga 标识
     * @param reason 失败原因
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(String sagaId, String reason) {
        instanceRepository.findBySagaId(sagaId).ifPresent(instance -> instance.fail(reason));
    }
}
