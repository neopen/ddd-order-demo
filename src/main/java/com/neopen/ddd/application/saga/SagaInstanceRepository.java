package com.neopen.ddd.application.saga;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Saga 实例仓储。
 */
@Repository
public interface SagaInstanceRepository extends JpaRepository<SagaInstance, Long> {

    /**
     * 按 Saga 业务标识查询。
     *
     * @param sagaId Saga 标识
     * @return 匹配的实例
     */
    Optional<SagaInstance> findBySagaId(String sagaId);

    /**
     * 查询业务单据最近一次 Saga 执行记录。
     *
     * @param businessKey 业务键，订单场景为订单号
     * @return 最近的 Saga 实例
     */
    Optional<SagaInstance> findTopByBusinessKeyOrderByIdDesc(String businessKey);

    /**
     * 查询长时间停留在运行状态的实例，供超时任务处理。
     *
     * @param status  目标状态
     * @param deadline 更新时间早于该时间即视为超时
     * @return 最多 20 条待处理实例
     */
    List<SagaInstance> findTop20ByStatusAndUpdatedAtBefore(SagaStatus status, LocalDateTime deadline);
}
