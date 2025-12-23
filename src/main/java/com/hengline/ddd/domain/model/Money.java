package com.hengline.ddd.domain.model;

import lombok.Getter;

import java.math.BigDecimal;

/**
 * 金额类：值对象
 */
@Getter
public class Money {
    private BigDecimal amount;
    private String currency;

    public Money(BigDecimal amount, String currency) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("金额不能为负");
        }
        this.amount = amount;
        this.currency = currency;
    }

    public Money add(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("货币不一致");
        }
        return new Money(this.amount.add(other.amount), this.currency);
    }

    // equals, hashCode 基于 amount 和 currency
}