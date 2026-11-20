package com.neopen.ddd.application.saga;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Saga 超时看门狗：把长时间停留在 RUNNING 的实例标记为 TIMEOUT。
 *
 * <p>解决的问题：编排器进程在某个步骤后崩溃时，Saga 会永久停留在 RUNNING，
 * 既没有失败记录也没有补偿动作。定时扫描可以把这类“静默悬挂”显式暴露出来，
 * 供告警或人工补偿处理。</p>
 *
 * <p>配置：{@code app.saga.timeout-seconds} 为超时阈值（默认 300 秒），
 * {@code app.saga.timeout-scan-interval-ms} 为扫描间隔（默认 60 秒），
 * {@code app.saga.timeout-enabled} 可关闭该任务。</p>
 */
@Component
@ConditionalOnProperty(
        prefix = "app.saga",
        name = "timeout-enabled",
        havingValue = "true",
        matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class SagaTimeoutJob {

    /** Saga 实例仓储。 */
    private final SagaInstanceRepository instanceRepository;

    /** 超时阈值（秒）。 */
    @Value("${app.saga.timeout-seconds:300}")
    private long timeoutSeconds;

    /**
     * 扫描并标记超时实例。
     * <p>每轮最多处理 20 条，避免单次事务过大；状态写回使用乐观锁，
     * 与正在执行的编排器并发时，冲突方会失败并由下一轮重试。</p>
     */
    @Scheduled(fixedDelayString = "${app.saga.timeout-scan-interval-ms:60000}")
    @Transactional
    public void markTimeouts() {
        LocalDateTime deadline = LocalDateTime.now().minusSeconds(timeoutSeconds);
        List<SagaInstance> stale = instanceRepository
                .findTop20ByStatusAndUpdatedAtBefore(SagaStatus.RUNNING, deadline);
        for (SagaInstance instance : stale) {
            instance.timeout();
            log.warn("Saga 执行超时: sagaId={}, businessKey={}, currentStep={}",
                    instance.getSagaId(), instance.getBusinessKey(), instance.getCurrentStep());
        }
    }
}
