package com.neopen.ddd.application.service;

import com.neopen.ddd.application.dto.*;
import com.neopen.ddd.domain.model.Order;
import com.neopen.ddd.domain.repository.OrderRepository;
import com.neopen.ddd.domain.repository.OrderStatusHistoryRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 订单查询服务（CQRS 的 Query 侧）。
 * <p>只读取数据并转换为视图对象，不修改聚合，也不触发状态机。</p>
 * <p>分层职责：属于命令查询职责分离 (CQRS) 的读侧，服务于列表与详情展示；
 * 读侧只读，不加载聚合行为 (Aggregate Behavior)，避免展示需求反向污染领域模型。</p>
 * <p>事务边界：类级只读事务，JPA 不会对查询结果做脏检查与自动 flush，
 * 因此任何误写都不会被持久化。需要写操作的场景必须走命令服务。</p>
 * <p>并发与异常：查询不涉及乐观锁；订单不存在时抛出 {@link IllegalArgumentException}，
 * 由全局异常处理器映射为 HTTP 400。读侧允许短暂陈旧数据，不保证与写侧强一致。</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderQueryService {

    /** 订单聚合仓储，只用于读取；查询方法不会调用聚合的变更行为。 */
    private final OrderRepository orderRepository;

    /** 状态流转历史仓储，按订单维度读取审计记录。 */
    private final OrderStatusHistoryRepository historyRepository;

    /**
     * 分页查询订单列表，条件来自 URL 查询参数。
     * <p>页码小于 0 时按 0 处理，每页条数被限制在 1 到 100 之间，防止恶意大分页；
     * 默认按创建时间倒序、同一时间再按主键倒序，保证翻页结果稳定。</p>
     *
     * @param query 查询条件与分页参数，非空
     * @return 分页结果，元素为列表摘要视图，按创建时间倒序排列
     */
    public PageResult<OrderSummaryView> list(OrderQuery query) {
        // 页码下界归零，条数夹在 1..100，避免越界或一次拉取过多数据。
        int page = Math.max(query.getPage(), 0);
        int size = Math.min(Math.max(query.getSize(), 1), 100);

        Pageable pageable = PageRequest.of(
                page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));

        return PageResult.from(
                orderRepository.findAll(buildSpecification(query), pageable)
                        .map(OrderSummaryView::from));
    }

    /**
     * 查询订单详情。
     * <p>为复用聚合到视图的映射而加载订单，但只读取字段，不执行任何业务方法。</p>
     *
     * @param orderId 订单标识
     * @return 订单详情视图，包含金额、预占标记、订单项与支付信息
     * @throws IllegalArgumentException 订单不存在
     */
    public OrderDetailView detail(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在: " + orderId));
        return OrderDetailView.from(order);
    }

    /**
     * 查询订单状态流转历史，按创建时间与主键升序排列，还原真实流转顺序。
     *
     * @param orderId 订单标识
     * @return 状态流转历史视图列表；订单存在但没有历史时返回空列表
     * @throws IllegalArgumentException 订单不存在
     */
    public List<OrderStatusHistoryView> history(Long orderId) {
        // 先确认订单存在，避免把“无历史”与“订单不存在”混为一谈。
        if (!orderRepository.existsById(orderId)) {
            throw new IllegalArgumentException("订单不存在: " + orderId);
        }
        return historyRepository.findByOrderIdOrderByCreatedAtAscIdAsc(orderId)
                .stream()
                .map(OrderStatusHistoryView::from)
                .toList();
    }

    /**
     * 根据查询条件动态拼装 JPA 规范 (Specification)。
     * <p>状态与客户标识为精确匹配，订单号为忽略大小写的模糊匹配；条件全部为空时
     * 返回恒真谓词，等价于全量查询。</p>
     *
     * @param query 查询条件，非空
     * @return 组合后的查询规范
     */
    private Specification<Order> buildSpecification(OrderQuery query) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query.getStatus() != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), query.getStatus()));
            }
            if (query.getCustomerId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("customerId"), query.getCustomerId()));
            }
            if (StringUtils.hasText(query.getOrderNum())) {
                // 关键字中的 % 与 _ 未转义，会按 SQL 通配符语义参与匹配。
                String keyword = "%" + query.getOrderNum().trim().toLowerCase() + "%";
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("orderNum")), keyword));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
