package com.neopen.ddd.domain.model;

/**
 * 订单定价结果值对象 (Value Object)：原始金额、优惠金额、应付金额及命中的优惠策略。
 * <p>由定价领域服务计算后传给聚合根，聚合据此写入订单的金额字段；
 * 作为 {@code record} 天然不可变，创建后不能再修改。</p>
 * <p>不变量：三个金额币种一致，且“应付金额 = 原始金额 - 优惠金额”，
 * 在构造期校验，防止定价逻辑与聚合内金额出现偏差。</p>
 *
 * @param originalAmount    优惠前的订单原始金额，即订单项小计之和
 * @param discountAmount    命中的优惠金额，未命中时为同币种零金额
 * @param payableAmount     用户实际应付金额，必须等于原始金额减去优惠金额
 * @param policyCode        命中的优惠策略编码，未命中时为 {@code NONE}
 * @param policyDescription 优惠策略描述，用于展示与问题排查
 */
public record OrderPricing(
        Money originalAmount,
        Money discountAmount,
        Money payableAmount,
        String policyCode,
        String policyDescription) {

    /**
     * 校验定价结果的完整性与一致性。
     *
     * @throws IllegalArgumentException 任一金额为空、三个金额币种不一致，
     *                                  或应付金额不等于原始金额减优惠金额时抛出
     */
    public OrderPricing {
        if (originalAmount == null || discountAmount == null || payableAmount == null) {
            throw new IllegalArgumentException("定价金额不能为空");
        }
        if (!originalAmount.getCurrency().equals(discountAmount.getCurrency())
                || !originalAmount.getCurrency().equals(payableAmount.getCurrency())) {
            throw new IllegalArgumentException("定价金额币种必须一致");
        }
        Money expected = originalAmount.subtract(discountAmount);
        if (!expected.equals(payableAmount)) {
            throw new IllegalArgumentException("应付金额必须等于原始金额减去优惠金额");
        }
    }

    /**
     * 构造“无优惠”的定价结果：优惠金额为零，应付金额等于原始金额。
     *
     * @param originalAmount 订单原始金额，同时作为应付金额
     * @return 策略编码为 {@code NONE} 的定价结果
     * @throws IllegalArgumentException 原始金额为空或金额校验不通过时抛出
     */
    public static OrderPricing none(Money originalAmount) {
        return new OrderPricing(
                originalAmount,
                Money.zero(originalAmount.getCurrency()),
                originalAmount,
                "NONE",
                "无优惠");
    }
}
