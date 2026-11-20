package com.neopen.ddd.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Objects;

/**
 * 金额值对象 (Value Object)：由金额和币种共同构成，不可变 (Immutable)。
 * <p>通过 {@code @Embeddable} 嵌入订单、订单项和支付记录，避免在各处重复维护金额与币种；
 * 所有运算方法都返回新实例，原实例保持不变，可安全共享。</p>
 * <p>不变量：金额非空且非负、统一保留两位小数（四舍五入）、币种非空并转为大写；
 * 加减与比较要求币种相同，防止跨币种直接运算。金额列统一为 DECIMAL(12,2)。</p>
 */
@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Money {

    /**
     * 系统默认结算币种，创建未显式指定币种的金额时使用。
     */
    public static final String DEFAULT_CURRENCY = "CNY";

    /**
     * 金额数值，单位为元。列 {@code amount} 为 DECIMAL(12,2)；
     * 构造时按 HALF_UP 四舍五入到两位，保证内存值与数据库精度一致。
     */
    @Column(name = "amount", precision = 12, scale = 2)
    private BigDecimal amount;

    /**
     * 币种代码，ISO 4217 形式的大写字符串（如 CNY、USD）。
     * <p>列长度 8，为未来币种扩展留余量；币种是值对象身份的一部分，参与判等。</p>
     */
    @Column(name = "currency", length = 8)
    private String currency;

    /**
     * 创建金额值对象，并完成规范化与基础校验。
     *
     * @param amount   金额，不能为空且不能为负，统一四舍五入到两位小数
     * @param currency 币种代码，不能为空；内部统一转为大写
     * @throws IllegalArgumentException 金额为空、金额为负或币种为空时抛出
     */
    public Money(BigDecimal amount, String currency) {
        if (amount == null) {
            throw new IllegalArgumentException("金额不能为空");
        }
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("金额不能为负");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("币种不能为空");
        }
        this.amount = amount.setScale(2, RoundingMode.HALF_UP);
        this.currency = currency.toUpperCase(Locale.ROOT);
    }

    /**
     * 以默认币种（人民币）创建金额。
     *
     * @param amount 金额，不能为空且不能为负
     * @return 使用 {@link #DEFAULT_CURRENCY} 的金额值对象
     * @throws IllegalArgumentException 金额为空或为负时抛出
     */
    public static Money of(BigDecimal amount) {
        return new Money(amount, DEFAULT_CURRENCY);
    }

    /**
     * 创建指定币种的零金额，常用于金额累加的初始值与无优惠场景。
     *
     * @param currency 币种代码，不能为空
     * @return 金额为 0 的金额值对象
     * @throws IllegalArgumentException 币种为空时抛出
     */
    public static Money zero(String currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    /**
     * 金额相加，返回新实例，不修改当前对象。
     *
     * @param other 被加金额，币种必须与当前金额一致
     * @return 相加后的新金额
     * @throws NullPointerException     被加金额为空时抛出
     * @throws IllegalArgumentException 币种不一致时抛出
     */
    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    /**
     * 金额相减，返回新实例，不修改当前对象。
     * <p>结果不允许为负，因此调用方需保证被减金额不小于当前金额。</p>
     *
     * @param other 被减金额，币种必须与当前金额一致
     * @return 相减后的新金额
     * @throws NullPointerException     被减金额为空时抛出
     * @throws IllegalArgumentException 币种不一致或结果金额为负时抛出
     */
    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), this.currency);
    }

    /**
     * 金额乘以系数（如单价 × 数量），结果按构造规则保留两位小数。
     *
     * @param factor 非空且非负的乘数，通常为购买数量
     * @return 相乘后的新金额
     * @throws IllegalArgumentException 乘数为空或为负时抛出
     */
    public Money multiply(BigDecimal factor) {
        if (factor == null || factor.signum() < 0) {
            throw new IllegalArgumentException("乘数不能为空或负数");
        }
        return new Money(this.amount.multiply(factor), this.currency);
    }

    /**
     * 判断金额是否为零，常用于判断订单是否免支付、优惠是否生效。
     *
     * @return 金额为 0 时返回 {@code true}
     */
    public boolean isZero() {
        return amount.signum() == 0;
    }

    /**
     * 判断金额是否为正数，用于校验订单总额、优惠金额的有效性。
     *
     * @return 金额大于 0 时返回 {@code true}
     */
    public boolean isPositive() {
        return amount.signum() > 0;
    }

    /**
     * 按数值比较两个同币种金额的大小，用于挑选最优惠的策略等场景。
     *
     * @param other 被比较金额，币种必须与当前金额一致
     * @return 负数、0、正数分别表示小于、等于、大于
     * @throws NullPointerException     被比较金额为空时抛出
     * @throws IllegalArgumentException 币种不一致时抛出
     */
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return this.amount.compareTo(other.amount);
    }

    /**
     * 校验参与运算的两个金额币种一致。
     * <p>金额只有在币种相同的前提下才能相加、相减或比较，
     * 否则结果没有业务意义，因此在这里统一拦截。</p>
     *
     * @param other 参与运算的另一个金额
     * @throws NullPointerException     传入 {@code null} 时抛出
     * @throws IllegalArgumentException 币种不一致时抛出
     */
    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "金额不能为空");
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "币种不一致: " + this.currency + " 与 " + other.currency);
        }
    }

    /**
     * 输出“金额 币种”形式的可读文本，仅用于日志与调试，不参与业务判断。
     *
     * @return 形如 {@code 100.00 CNY} 的字符串
     */
    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency;
    }
}
