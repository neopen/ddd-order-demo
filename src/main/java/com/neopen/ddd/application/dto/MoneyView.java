package com.neopen.ddd.application.dto;

import com.neopen.ddd.domain.model.Money;

import java.math.BigDecimal;

/**
 * 金额视图对象。
 * <p>读侧对金额值对象的扁平化表示，避免直接暴露领域模型。</p>
 *
 * @param amount   金额数值，单位为元，保留两位小数
 * @param currency 币种代码，大写形式，如 CNY
 */
public record MoneyView(BigDecimal amount, String currency) {

    /**
     * 由金额值对象构建视图。
     *
     * @param money 金额值对象，可能为 null
     * @return 金额视图；入参为 null 时返回 null
     */
    public static MoneyView from(Money money) {
        if (money == null) {
            return null;
        }
        return new MoneyView(money.getAmount(), money.getCurrency());
    }
}
