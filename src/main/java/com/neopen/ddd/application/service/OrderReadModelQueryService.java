package com.neopen.ddd.application.service;

import com.neopen.ddd.application.dto.OrderReadModelView;
import com.neopen.ddd.infrastructure.readmodel.OrderSummaryProjection;
import com.neopen.ddd.infrastructure.readmodel.OrderSummaryProjectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 订单读模型查询服务（CQRS 的 Query 侧独立实现）。
 *
 * <p>与 {@link OrderQueryService} 的区别：后者直接读取写模型聚合（强一致但需要联表），
 * 本类只读取投影表，是事件驱动 CQRS 的典型读路径——查询不触碰订单聚合，
 * 展示字段与写模型解耦，可独立扩展与缓存。</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderReadModelQueryService {

    private final OrderSummaryProjectionRepository projectionRepository;

    /**
     * 查询最近更新的读模型列表。
     *
     * @param status 可选状态过滤，为空表示全部
     * @return 最多 100 条读模型视图
     */
    public List<OrderReadModelView> list(String status) {
        List<OrderSummaryProjection> projections = StringUtils.hasText(status)
                ? projectionRepository.findTop100ByStatusOrderByUpdatedAtDesc(status)
                : projectionRepository.findTop100ByOrderByUpdatedAtDesc();
        return projections.stream().map(this::toView).toList();
    }

    /**
     * 按订单号查询读模型。
     *
     * @param orderNum 订单号
     * @return 读模型视图
     * @throws IllegalArgumentException 投影不存在
     */
    public OrderReadModelView detail(String orderNum) {
        return projectionRepository.findById(orderNum)
                .map(this::toView)
                .orElseThrow(() -> new IllegalArgumentException("读模型不存在: " + orderNum));
    }

    /**
     * 把投影实体转换为视图。
     *
     * @param projection 投影实体
     * @return 读模型视图
     */
    private OrderReadModelView toView(OrderSummaryProjection projection) {
        return new OrderReadModelView(
                projection.getOrderNum(),
                projection.getOrderId(),
                projection.getCustomerId(),
                projection.getStatus(),
                projection.getPayableAmount(),
                projection.getCurrency(),
                projection.getUpdatedAt(),
                projection.getLastEventType());
    }
}
