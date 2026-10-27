package com.neopen.ddd.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 订单实体 - 聚合根
 * 表示客户的一个订单，包含多个订单项
 *
 * 维护状态、preStatus、金额等领域数据
 */
@Entity
@Table(
        name = "tab_order",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_orders_order_num",
                        columnNames = "order_num"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA需要无参构造函数，但限制为protected以强制通过工厂方法创建
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_num", nullable = false, updatable = false, length = 64)
    private String orderNum;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    /**
     * 退款前状态，用于退款失败后恢复。
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "pre_status", length = 32)
    private OrderStatus preStatus;

    @Column(
            name = "total_amount",
            nullable = false,
            precision = 10,
            scale = 2
    )
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(
            mappedBy = "order",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<ProductItem> productItems = new ArrayList<>();

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "payment_id", unique = true)
    private OrderPayment orderPayment;

    /**
     * JPA 乐观锁版本号。
     */
    @Version
    private Long version;

    // ==================== 创建订单 ====================

    public static Order create(
            Long customerId,
            List<ProductItem> items) {

        if (customerId == null) {
            throw new IllegalArgumentException("客户ID不能为空");
        }

        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("订单项不能为空");
        }

        Order order = new Order();
        // id 由数据库生成，不要手动赋值。
        order.orderNum = generateOrderNumber();
        order.customerId = customerId;
        order.status = OrderStatus.CREATED;
        order.createdAt = LocalDateTime.now();
        order.updatedAt = order.createdAt;

        for (ProductItem item : items) {
            order.addOrderItem(item);
        }

        order.recalculateTotalAmount();

        if (order.totalAmount.signum() <= 0) {
            throw new IllegalArgumentException("订单总金额必须大于零");
        }

        return order;
    }

    // ==================== 订单项管理 ====================

    public void addOrderItem(ProductItem item) {
        if (status != OrderStatus.CREATED) {
            throw new IllegalStateException(
                    "只有待支付订单才能修改订单项"
            );
        }

        Objects.requireNonNull(item, "订单项不能为空");

        if (productItems.contains(item)) {
            return;
        }

        productItems.add(item);
        item.setOrder(this);
        recalculateTotalAmount();
        touch();
    }

    public void removeOrderItem(ProductItem item) {
        if (status != OrderStatus.CREATED) {
            throw new IllegalStateException(
                    "只有待支付订单才能移除订单项"
            );
        }

        if (productItems.remove(item)) {
            item.setOrder(null);
            recalculateTotalAmount();
            touch();
        }
    }

    public List<ProductItem> getProductItems() {
        return Collections.unmodifiableList(productItems);
    }

    private void recalculateTotalAmount() {
        this.totalAmount = productItems.stream()
                .map(ProductItem::getSubtotal)
                .peek(amount -> {
                    if (amount == null || amount.signum() < 0) {
                        throw new IllegalArgumentException(
                                "订单项金额不能为 null 或负数"
                        );
                    }
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ==================== 支付信息 ====================

    /**
     * 记录已由支付渠道确认的支付结果，不改变订单状态。
     * 订单状态迁移仍必须通过状态机应用服务完成。
     */
    public void recordPayment(OrderPayment paymentInfo) {
        if (status != OrderStatus.CREATED) {
            throw new IllegalStateException("只有待支付订单才能记录支付信息，当前状态：" + status);
        }
        Objects.requireNonNull(paymentInfo, "支付信息不能为空");
        if (this.orderPayment != null) {
            throw new IllegalStateException("订单已存在支付记录");
        }
        this.orderPayment = paymentInfo;
        paymentInfo.setOrder(this);
        touch();
    }

    // ==================== 状态机同步 ====================

    /**
     * 应用状态机已验证的迁移结果。
     * <p>只有状态机应用服务应调用此方法；合法迁移规则集中定义在 OrderStateMachineConfig。
     * 这里仅维护聚合内部字段不变量，不再重复维护一份状态迁移表。
     */
    public void transitTo(OrderStatus target) {
        Objects.requireNonNull(target, "目标状态不能为空");

        if (target == OrderStatus.REFUNDING) {
            if (status != OrderStatus.PAID
                    && status != OrderStatus.SHIPPED
                    && status != OrderStatus.DELIVERED) {
                throw new IllegalStateException("当前状态不允许进入退款中：" + status);
            }
            this.preStatus = this.status;
        } else if (this.status == OrderStatus.REFUNDING
                && target == preStatus) {
            if (preStatus == null) {
                throw new IllegalStateException("退款前状态缺失，无法恢复订单");
            }
            this.preStatus = null;
        } else if (target == OrderStatus.REFUNDED) {
            if (status != OrderStatus.REFUNDING) {
                throw new IllegalStateException("只有退款中的订单才能确认退款");
            }
            this.preStatus = null;
        }

        this.status = target;
        touch();
    }

    // ==================== 内部方法 ====================

    private void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    private static String generateOrderNumber() {
        return "ORD"
                + System.currentTimeMillis()
                + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8);
    }
}
