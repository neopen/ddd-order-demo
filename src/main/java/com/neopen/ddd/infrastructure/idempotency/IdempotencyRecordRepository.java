package com.neopen.ddd.infrastructure.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 幂等 (Idempotency) 记录仓库。
 *
 * <p>按作用域与幂等键查询首次执行结果；插入冲突由数据库唯一约束
 * {@code uk_idempotency_scope_key} 检测，并以数据完整性异常的形式抛出。</p>
 */
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    /**
     * 按作用域与幂等键查询已记录的执行结果。
     *
     * @param scope   幂等作用域
     * @param idemKey 幂等键，调用前应已去除首尾空白
     * @return 匹配的记录；不存在时返回 {@link Optional#empty()}
     */
    Optional<IdempotencyRecord> findByScopeAndIdemKey(String scope, String idemKey);
}
