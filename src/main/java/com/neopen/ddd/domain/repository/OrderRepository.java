package com.neopen.ddd.domain.repository;

import com.neopen.ddd.domain.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 订单聚合根 (Aggregate Root) 的仓储 (Repository)。
 * <p>在 DDD 中，仓储以“集合”的抽象访问聚合，负责持久化细节与重建聚合对象，
 * 领域层只依赖该接口，从而与 JPA / 数据库隔离。</p>
 *
 * <p>使用约定：</p>
 * <ul>
 *   <li>每次事务应通过仓储按聚合 ID 加载聚合，修改后由工作单元统一写回，
 *       避免同一聚合被重复实例化导致不变量失效。</li>
 *   <li>擅长按主键或业务键查询；复杂筛选条件由 {@link JpaSpecificationExecutor}
 *       的规格查询承担，避免为每个查询组合新增方法。</li>
 *   <li>方法通常运行在调用方事务内，仓储自身不控制事务边界。</li>
 * </ul>
 */
@Repository
public interface OrderRepository
        extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    /**
     * 按订单号查询订单。
     * <p>订单号是对外暴露的业务标识，用于接口回调、重复下单校验等场景；
     * 返回 {@link Optional} 是因为订单号可能不存在或已被逻辑删除，
     * 由调用方决定是抛业务异常还是走兜底逻辑。</p>
     *
     * @param orderNum 订单号
     * @return 匹配的订单，不存在时为空 {@link Optional}
     */
    Optional<Order> findByOrderNum(String orderNum);
}
