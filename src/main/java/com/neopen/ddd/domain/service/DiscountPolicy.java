package com.neopen.ddd.domain.service;

import com.neopen.ddd.domain.model.Money;

/**
 * 优惠策略接口（策略模式 (Strategy Pattern) 的抽象）。
 * <p>作为定价领域中的扩展点：新增优惠规则只需实现该接口并注册为 Spring Bean，
 * 由 {@link OrderPricingService} 自动收集，无需修改订单聚合或定价服务本身，
 * 从而满足开闭原则。</p>
 *
 * <p>契约（实现方必须遵守）：</p>
 * <ul>
 *   <li>{@link #discountFor(Money)} 必须为纯函数：相同入参恒定返回相同结果，不得有副作用。</li>
 *   <li>返回金额的币种必须与入参一致，便于上层直接比较大小。</li>
 *   <li>优惠不可用时返回同币种零金额，而不是 {@code null}，避免上层做空值分支。</li>
 *   <li>减免金额不得超过原始金额，保证应付金额非负。</li>
 *   <li>{@link #code()} 需全局唯一且稳定，它是平局时的排序键，也是定价结果中的策略标识。</li>
 * </ul>
 */
public interface DiscountPolicy {

    /**
     * 策略编码，作为稳定且唯一的业务标识。
     * <p>用于定价结果展示与平局排序；一旦对外暴露（如订单快照、报表）就不应随意变更。</p>
     *
     * @return 策略编码，例如 {@code FULL_REDUCTION}
     */
    String code();

    /**
     * 策略的中文描述，用于订单页面展示优惠来源。
     *
     * @return 面向用户的优惠名称
     */
    String description();

    /**
     * 计算该策略针对原始金额可减免的金额。
     * <p>实现应按自身档位规则判断是否命中，未命中时返回同币种零金额。</p>
     *
     * @param originalAmount 优惠前的订单原始金额，不可为 {@code null}
     * @return 针对原始金额可减免的金额，不可用时返回同币种的零金额
     */
    Money discountFor(Money originalAmount);
}
