package com.neopen.ddd.infrastructure.messaging.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 事务性发件箱 (Transactional Outbox) 仓库。
 *
 * <p>基于 Spring Data JPA 派生查询实现，负责按状态分批读取待投递记录；
 * 事务边界由调用方（{@link OutboxRelay}）统一控制，本接口不声明事务语义。</p>
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * 按主键升序获取指定状态的最多 20 条记录，作为一轮中继的投递批次。
     *
     * <p>主键升序保证较老的事件优先投递；20 条上限用于控制单次事务耗时，
     * 避免一次处理过多记录导致长事务。</p>
     *
     * @param status 目标状态，中继通常传入 {@link OutboxStatus#PENDING}
     * @return 匹配的记录列表，无数据时为空列表而非 {@code null}
     */
    List<OutboxEvent> findTop20ByStatusOrderByIdAsc(OutboxStatus status);
}
