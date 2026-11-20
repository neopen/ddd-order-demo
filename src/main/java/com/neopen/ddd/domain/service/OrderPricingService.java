package com.neopen.ddd.domain.service;

import com.neopen.ddd.domain.model.Money;
import com.neopen.ddd.domain.model.OrderPricing;
import com.neopen.ddd.domain.model.ProductItem;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 订单定价领域服务 (Domain Service)。
 * <p>定价需要协调“订单项汇总”与“多个优惠策略比价”，逻辑跨多个领域对象且不属于
 * 单一实体的职责，因此以领域服务承载，保证订单聚合不依赖具体的优惠实现。</p>
 *
 * <p>选优规则：遍历所有策略，取优惠金额最大者；金额相同时按策略编码
 * ({@link DiscountPolicy#code()}) 字典序升序，保证同一输入在任何时刻、任何节点上
 * 都得到相同的定价结果（即平局可复现、结果稳定）。</p>
 *
 * <p>事务与并发：本服务无状态，策略列表在构造时拷贝为不可变列表，可安全地被并发复用；
 * 服务本身不开启事务，是否处于调用方事务边界内由应用层决定。</p>
 */
@Service
public class OrderPricingService {

    /** 参与比价的优惠策略集合，构造后不可变，顺序不影响结果。 */
    private final List<DiscountPolicy> policies;

    /**
     * 注入所有已注册的优惠策略。
     * <p>做空值与拷贝处理：避免外部修改集合影响服务内部状态，也避免依赖注入为
     * {@code null} 时在定价阶段抛空指针；无策略时退化为“无优惠”。</p>
     *
     * @param policies Spring 容器中的全部优惠策略，允许为 {@code null} 或空列表
     */
    public OrderPricingService(List<DiscountPolicy> policies) {
        this.policies = policies == null ? List.of() : List.copyOf(policies);
    }

    /**
     * 计算订单的原始金额、最优优惠与应付金额。
     * <p>处理流程：汇总订单项小计得到原始金额（币种取自首个订单项，隐含“同一订单内
     * 币种一致”的不变量，不一致时金额运算会抛异常）→ 逐策略询价 →
     * 选出优惠最大者 → 组装定价结果。若没有任何策略命中（全为零金额），返回
     * {@code OrderPricing.none(original)} 表示无优惠，而不是返回 {@code null}。</p>
     *
     * @param items 订单项列表，不能为空且各项已计算小计
     * @return 定价结果值对象，包含原始金额、优惠金额、应付金额与命中的策略信息
     * @throws IllegalArgumentException 订单项为空时抛出
     * @throws NullPointerException     某个策略返回 {@code null} 优惠金额时抛出
     */
    public OrderPricing price(List<ProductItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("订单项不能为空");
        }

        String currency = items.get(0).getSubtotal().getCurrency();
        Money original = items.stream()
                .map(ProductItem::getSubtotal)
                .reduce(Money.zero(currency), Money::add);

        DiscountPolicy bestPolicy = null;
        Money bestDiscount = Money.zero(currency);

        for (DiscountPolicy policy : policies) {
            // 策略契约要求返回零金额而非 null，这里仍显式校验，尽早暴露违规实现。
            Money discount = Objects.requireNonNull(
                    policy.discountFor(original), "优惠金额不能为空");
            // 金额更大者胜出；金额相同时以策略编码字典序较小者胜出，保证结果稳定可复现。
            if (discount.compareTo(bestDiscount) > 0
                    || (discount.compareTo(bestDiscount) == 0
                    && bestPolicy != null
                    && policy.code().compareTo(bestPolicy.code()) < 0)) {
                bestDiscount = discount;
                bestPolicy = policy;
            }
        }

        // 无策略命中（或命中但减免为零）时统一退化为“无优惠”，避免产生零优惠的定价快照。
        if (bestPolicy == null || bestDiscount.isZero()) {
            return OrderPricing.none(original);
        }

        return new OrderPricing(
                original,
                bestDiscount,
                original.subtract(bestDiscount),
                bestPolicy.code(),
                bestPolicy.description());
    }
}
