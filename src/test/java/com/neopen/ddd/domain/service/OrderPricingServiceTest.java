package com.neopen.ddd.domain.service;

import com.neopen.ddd.domain.model.Money;
import com.neopen.ddd.domain.model.OrderPricing;
import com.neopen.ddd.domain.model.ProductItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 订单定价领域服务单元测试：验证优惠门槛、满减与折扣的命中及多策略选优规则。
 * <p>测试范围：直接实例化服务并注入两个真实策略，不启动 Spring 容器、不访问数据库。</p>
 * <p>Mock 策略：不使用 Mock，两个策略都是无状态纯函数实现，用真实对象验证比价结果
 * 更贴近运行时行为。</p>
 */
class OrderPricingServiceTest {

    /** 被测服务：注入满减与折扣两个策略，验证二者比价后的最终定价。 */
    private final OrderPricingService pricingService = new OrderPricingService(List.of(
            new FullReductionDiscountPolicy(),
            new PercentageDiscountPolicy()));

    /**
     * 业务场景：订单金额 99 元，低于所有优惠门槛。
     * <p>关键断言：原始金额保持 99.00，优惠金额为 0，策略编码为 NONE。</p>
     */
    @Test
    @DisplayName("未达到门槛时不优惠")
    void shouldNotDiscountBelowThreshold() {
        // 准备：订单仅含一个 99 元的商品项
        // 执行：对 99 元的单个订单项定价
        OrderPricing pricing = pricingService.price(List.of(item("99.00", 1)));

        // 断言：满减未达 100、折扣未达 200，均不生效
        assertEquals(new BigDecimal("99.00"), pricing.originalAmount().getAmount());
        assertEquals(new BigDecimal("0.00"), pricing.discountAmount().getAmount());
        assertEquals("NONE", pricing.policyCode());
    }

    /**
     * 业务场景：订单金额恰好 100 元，触及满减最低门槛。
     * <p>关键断言：减免 10 元，应付 90 元，命中策略编码 FULL_REDUCTION。</p>
     */
    @Test
    @DisplayName("满 100 使用满减策略")
    void shouldApplyFullReduction() {
        // 准备：订单金额为 100 元，恰好达到满减门槛
        // 执行：对恰好 100 元的订单定价
        OrderPricing pricing = pricingService.price(List.of(item("100.00", 1)));

        // 断言：满减档位生效，折扣策略因未达 200 元不参与竞争
        assertEquals(new BigDecimal("10.00"), pricing.discountAmount().getAmount());
        assertEquals(new BigDecimal("90.00"), pricing.payableAmount().getAmount());
        assertEquals("FULL_REDUCTION", pricing.policyCode());
    }

    /**
     * 业务场景：订单金额 1000 元，两个策略同时命中。
     * <p>关键断言：满减 500 档减免 60 元，9 折减免 100 元，最终取优惠更大的折扣策略。</p>
     */
    @Test
    @DisplayName("多个策略命中时选择优惠金额最大的策略")
    void shouldChooseBestPolicy() {
        // 准备：订单金额为 1000 元，满减与折扣两个策略可同时命中
        // 1000 元：满减 60 元，9 折优惠 100 元，应选择折扣策略
        OrderPricing pricing = pricingService.price(List.of(item("1000.00", 1)));

        // 断言：优惠金额与应付金额均体现较优的折扣策略
        assertEquals(new BigDecimal("100.00"), pricing.discountAmount().getAmount());
        assertEquals(new BigDecimal("900.00"), pricing.payableAmount().getAmount());
        assertEquals("PERCENTAGE", pricing.policyCode());
    }

    /**
     * 构造单个订单项：单价乘以数量得到小计，币种固定为人民币。
     *
     * @param price    商品单价
     * @param quantity 购买数量
     * @return 订单项值对象
     */
    private ProductItem item(String price, int quantity) {
        return ProductItem.create(1L, "测试商品", quantity,
                new Money(new BigDecimal(price), "CNY"));
    }
}
