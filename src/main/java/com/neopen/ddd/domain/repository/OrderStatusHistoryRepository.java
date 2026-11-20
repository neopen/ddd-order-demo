package com.neopen.ddd.domain.repository;

import com.neopen.ddd.domain.model.OrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderStatusHistoryRepository
        extends JpaRepository<OrderStatusHistory, Long> {

    /**
     * 按时间顺序查询订单状态流转历史。
     * <p>排序键先按创建时间、再按主键，便于还原同一时刻发生的多次迁移；
     * 返回列表可能为空，表示该订单尚无历史记录。</p>
     *
     * @param orderId 订单主键
     * @return 按发生顺序升序排列的历史记录，可能为空列表
     */
    List<OrderStatusHistory> findByOrderIdOrderByCreatedAtAscIdAsc(
            Long orderId);
}