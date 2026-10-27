package com.neopen.ddd.domain.repository;

import com.neopen.ddd.domain.model.OrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderStatusHistoryRepository
        extends JpaRepository<OrderStatusHistory, Long> {

    /**
     * 按时间顺序查询订单状态流转历史
     */
    List<OrderStatusHistory> findByOrderIdOrderByCreatedAtAscIdAsc(
            Long orderId);
}