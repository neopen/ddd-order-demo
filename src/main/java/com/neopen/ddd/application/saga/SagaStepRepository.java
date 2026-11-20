package com.neopen.ddd.application.saga;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Saga 步骤仓储。
 */
@Repository
public interface SagaStepRepository extends JpaRepository<SagaStep, Long> {

    /**
     * 按 Saga 标识顺序读取全部步骤。
     *
     * @param sagaId Saga 标识
     * @return 按主键升序的步骤列表
     */
    List<SagaStep> findBySagaIdOrderByIdAsc(String sagaId);

    /**
     * 查询指定步骤，重试与状态更新时复用同一条记录。
     *
     * @param sagaId   Saga 标识
     * @param stepName 步骤名
     * @return 匹配的步骤
     */
    Optional<SagaStep> findBySagaIdAndStepName(String sagaId, String stepName);
}
