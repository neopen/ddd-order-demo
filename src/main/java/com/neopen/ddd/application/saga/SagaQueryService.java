package com.neopen.ddd.application.saga;

import com.neopen.ddd.application.dto.SagaDetailView;
import com.neopen.ddd.application.dto.SagaStepView;
import com.neopen.ddd.domain.model.Order;
import com.neopen.ddd.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Saga 查询服务：供运维与联调查看一次跨服务流程的执行轨迹。
 *
 * <p>只读服务，不修改 Saga 状态，也不触发补偿；补偿由编排器在失败时同步完成，
 * 超时由 {@link SagaTimeoutJob} 标记。查询接口让“分布式事务到底做到哪一步”
 * 变成可观测的数据，而不是只存在于日志里。</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SagaQueryService {

    private final SagaInstanceRepository instanceRepository;
    private final SagaStepRepository stepRepository;
    private final OrderRepository orderRepository;

    /**
     * 按订单查询最近一次创建 Saga。
     *
     * @param orderId 订单主键
     * @return Saga 详情
     * @throws IllegalArgumentException 订单不存在或没有 Saga 记录
     */
    public SagaDetailView findByOrderId(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在: " + orderId));
        SagaInstance instance = instanceRepository.findTopByBusinessKeyOrderByIdDesc(order.getOrderNum())
                .orElseThrow(() -> new IllegalArgumentException("订单没有对应的 Saga 记录: " + orderId));
        return toView(instance);
    }

    /**
     * 按 Saga 标识查询执行轨迹。
     *
     * @param sagaId Saga 标识
     * @return Saga 详情
     * @throws IllegalArgumentException Saga 不存在
     */
    public SagaDetailView findBySagaId(String sagaId) {
        SagaInstance instance = instanceRepository.findBySagaId(sagaId)
                .orElseThrow(() -> new IllegalArgumentException("Saga 不存在: " + sagaId));
        return toView(instance);
    }

    /**
     * 组装视图：实例字段 + 步骤明细。
     *
     * @param instance Saga 实例
     * @return 详情视图
     */
    private SagaDetailView toView(SagaInstance instance) {
        List<SagaStepView> steps = stepRepository
                .findBySagaIdOrderByIdAsc(instance.getSagaId())
                .stream()
                .map(step -> new SagaStepView(
                        step.getStepName(),
                        step.getStatus().name(),
                        step.getAttempts(),
                        step.getErrorMessage(),
                        step.getStartedAt(),
                        step.getFinishedAt()))
                .toList();
        return new SagaDetailView(
                instance.getSagaId(),
                instance.getSagaType(),
                instance.getBusinessKey(),
                instance.getStatus().name(),
                instance.getCurrentStep(),
                instance.getErrorMessage(),
                instance.getStartedAt(),
                instance.getFinishedAt(),
                steps);
    }
}
