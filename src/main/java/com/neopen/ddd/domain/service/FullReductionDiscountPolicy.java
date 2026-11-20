package com.neopen.ddd.domain.service;

import com.neopen.ddd.domain.model.Money;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 满减优惠策略：满 100 减 10，满 200 减 20，满 500 减 60。
 * <p>属于定价领域中的无状态策略实现，注册为 Spring Bean 后由 {@link OrderPricingService}
 * 自动收集参与比价。档位判断必须从高到低进行，否则会命中较低的减免档位。</p>
 */
@Component
public class FullReductionDiscountPolicy implements DiscountPolicy {

    /**
     * @return 策略编码，常量值参与定价结果的平局排序
     */
    @Override
    public String code() {
        return "FULL_REDUCTION";
    }

    /**
     * @return 面向用户的优惠名称
     */
    @Override
    public String description() {
        return "满减优惠";
    }

    /**
     * 按金额档位计算满减金额。
     * <p>按从高到低顺序判断，确保大额订单拿到最优档位；未达最低门槛时返回零金额，
     * 表示该策略不适用，而不是抛出异常。</p>
     *
     * @param originalAmount 优惠前订单金额，不可为 {@code null}
     * @return 命中的减免金额；未达门槛时为同币种零金额
     */
    @Override
    public Money discountFor(Money originalAmount) {
        BigDecimal reduction;

        if (atLeast(originalAmount, "500")) {
            reduction = new BigDecimal("60");
        } else if (atLeast(originalAmount, "200")) {
            reduction = new BigDecimal("20");
        } else if (atLeast(originalAmount, "100")) {
            reduction = new BigDecimal("10");
        } else {
            reduction = BigDecimal.ZERO;
        }
        return new Money(reduction, originalAmount.getCurrency());
    }

    /**
     * 判断金额是否达到指定门槛（含等于）。
     * <p>用 {@link Money#compareTo(Money)} 比较而非字符串或 double，避免精度误差，
     * 同时该方法会校验币种一致，保证门槛金额与订单金额同币种。</p>
     *
     * @param amount    待比较金额
     * @param threshold 门槛金额的数值字符串，币种沿用 {@code amount}
     * @return 达到或超过门槛时为 {@code true}
     */
    private boolean atLeast(Money amount, String threshold) {
        return amount.compareTo(
                new Money(new BigDecimal(threshold), amount.getCurrency())) >= 0;
    }
}
