package com.hengline.ddd.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * 订单项实体
 * 表示订单中的一个商品项
 */
@Entity
@Table(name = "tab_order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA需要无参构造函数，但限制为protected以强制通过工厂方法创建
public class ProductItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    private Order order;

    /**
     * 创建订单项
     *
     * @param productId   商品ID
     * @param productName 商品名称
     * @param quantity    数量
     * @param unitPrice   单价
     * @return 订单项实例
     */
    public static ProductItem create(Long productId, String productName, int quantity, Money unitPrice) {
        if (productId == null) {
            throw new IllegalArgumentException("商品ID不能为空");
        }
        if (productName == null || productName.trim().isEmpty()) {
            throw new IllegalArgumentException("商品名称不能为空");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("商品数量必须大于0");
        }

        ProductItem item = new ProductItem();
        item.productId = productId;
        item.productName = productName;
        item.quantity = quantity;
        item.unitPrice = unitPrice.getAmount();
        item.subtotal = unitPrice.getAmount().multiply(BigDecimal.valueOf(quantity));
        return item;
    }

    /**
     * 更新商品数量
     *
     * @param newQuantity 新数量
     */
    public void updateQuantity(int newQuantity) {
        if (newQuantity <= 0) {
            throw new IllegalArgumentException("商品数量必须大于0");
        }
        this.quantity = newQuantity;
        recalculateSubtotal();
    }

    /**
     * 重新计算小计金额
     */
    private void recalculateSubtotal() {
        this.subtotal = this.unitPrice.multiply(BigDecimal.valueOf(this.quantity));
    }

}
