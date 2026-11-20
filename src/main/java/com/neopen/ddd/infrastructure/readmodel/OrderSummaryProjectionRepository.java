package com.neopen.ddd.infrastructure.readmodel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 订单读模型仓储。
 */
@Repository
public interface OrderSummaryProjectionRepository extends JpaRepository<OrderSummaryProjection, String> {

    /**
     * 按更新时间倒序读取最近投影，供列表页展示。
     *
     * @return 最多 100 条投影
     */
    List<OrderSummaryProjection> findTop100ByOrderByUpdatedAtDesc();

    /**
     * 按状态过滤并更新时间倒序读取。
     *
     * @param status 订单状态
     * @return 最多 100 条投影
     */
    List<OrderSummaryProjection> findTop100ByStatusOrderByUpdatedAtDesc(String status);
}
