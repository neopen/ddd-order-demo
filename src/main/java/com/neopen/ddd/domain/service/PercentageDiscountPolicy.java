package com.neopen.ddd.domain.service;

import com.neopen.ddd.domain.model.Money;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 折扣优惠策略：满 200 享 95 折，满 1000 享 9 折。
 * <p>实现上返回的是“减免金额”而非折后价：9 折对应减免原价的 10%，95 折对应 5%，
 * 由 {@link Money#multiply(BigDecimal)} 按比例计算并保留两位小数。</p>
 *
 * <p>同样是无状态 Bean，档位从高到低判断，保证大额订单命中更高折扣。</p>
 */
@Component
public class PercentageDiscountPolicy implements DiscountPolicy {

    /**
     * @return 策略编码，常量值参与定价结果的平局排序
     */
    @Override
    public String code() {
        return "PERCENTAGE";
    }

    /**
     * @return 面向用户的优惠名称
     */
    @Override
    public String description() {
        return "折扣优惠";
    }

    /**
     * 按金额档位计算折扣对应的减免金额。
     * <p>先判断高门槛再判断低门槛，否则 1000 元以上的订单会错误地只按 95 折计算；
     * 未达门槛时返回零金额，表示该策略不参与本次比价。</p>
     *
     * @param originalAmount 优惠前订单金额，不可为 {@code null}
     * @return 命中的减免金额；未达门槛时为同币种零金额
     */
    @Override
    public Money discountFor(Money originalAmount) {
        if (atLeast(originalAmount, "1000")) {
            return originalAmount.multiply(new BigDecimal("0.10"));
        }
        if (atLeast(originalAmount, "200")) {
            return originalAmount.multiply(new BigDecimal("0.05"));
        }
        return Money.zero(originalAmount.getCurrency());
    }

    /**
     * 判断金额是否达到指定门槛（含等于）。
     * <p>通过金额值对象比较，天然校验币种一致并规避浮点精度问题。</p>
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
