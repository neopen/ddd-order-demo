package com.neopen.ddd.domain.model;

import com.neopen.ddd.domain.event.DomainEvent;
import com.neopen.ddd.domain.event.OrderCancelledEvent;
import com.neopen.ddd.domain.event.OrderPaidEvent;
import com.neopen.ddd.domain.event.OrderShippedEvent;
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

    /**
     * 当前订单实例的领域事件，不同订单互不共享。
     * transient 字段不参与 JPA 持久化。
     */
    @Transient
    private transient List<DomainEvent> events = new ArrayList<>();

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

    // ==================== 支付 ====================

    /**
     * 支付成功：CREATED -> PAID
     * <p>
     * 应在支付结果已被可信渠道确认后调用。
     */
    public void pay(OrderPayment paymentInfo) {
        requireStatus(OrderStatus.CREATED, "只有待支付订单才能支付");
        if(paymentInfo == null) {
            return;
        }

        this.status = OrderStatus.PAID;
        this.orderPayment = paymentInfo;
        touch();

        registerEvent(
                new OrderPaidEvent(
                        id,
                        new Money(totalAmount, "CNY")
                )
        );
    }

    // ==================== 发货与签收 ====================

    /**
     * 发货：PAID -> SHIPPED
     */
    public void ship() {
        requireStatus(OrderStatus.PAID, "只有已支付订单才能发货");

        this.status = OrderStatus.SHIPPED;
        touch();

        registerEvent(new OrderShippedEvent(id));
    }

    /**
     * 签收：SHIPPED -> DELIVERED
     */
    public void sign() {
        requireStatus(OrderStatus.SHIPPED, "只有已发货订单才能签收");

        this.status = OrderStatus.DELIVERED;
        touch();
    }

    /**
     * 完成：DELIVERED -> COMPLETED
     */
    public void complete() {
        requireStatus(OrderStatus.DELIVERED, "只有已签收订单才能完成");

        this.status = OrderStatus.COMPLETED;
        touch();
    }

    // ==================== 取消订单 ====================

    /**
     * 取消待支付订单：CREATED -> CANCELLED。
     * <p>
     * 已支付订单涉及资金处理，不在此方法中直接取消。
     */
    public void cancel() {
        requireStatus(
                OrderStatus.CREATED,
                "只有待支付订单才能直接取消"
        );

        this.status = OrderStatus.CANCELLED;
        touch();

        registerEvent(new OrderCancelledEvent(id, customerId));
    }

    // ==================== 退款 ====================

    /**
     * 发起退款：PAID / SHIPPED / DELIVERED -> REFUNDING。
     */
    public void startRefund() {
        if (status != OrderStatus.PAID
                && status != OrderStatus.SHIPPED
                && status != OrderStatus.DELIVERED) {
            throw new IllegalStateException(
                    "当前状态不允许退款：" + status
            );
        }

        this.preStatus = this.status;
        this.status = OrderStatus.REFUNDING;
        touch();
    }

    /**
     * 退款失败：REFUNDING -> 退款前状态。
     */
    public void rollbackRefund() {
        requireStatus(
                OrderStatus.REFUNDING,
                "只有退款中的订单才能恢复"
        );

        if (preStatus == null) {
            throw new IllegalStateException(
                    "退款前状态缺失，无法恢复订单"
            );
        }

        this.status = preStatus;
        this.preStatus = null;
        touch();
    }

    /**
     * 退款成功：REFUNDING -> REFUNDED。
     */
    public void markRefunded() {
        requireStatus(
                OrderStatus.REFUNDING,
                "只有退款中的订单才能确认退款"
        );

        this.status = OrderStatus.REFUNDED;
        this.preStatus = null;
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

    // ==================== 领域事件 ====================

    protected void registerEvent(DomainEvent event) {
        Objects.requireNonNull(event, "领域事件不能为空");

        if (events == null) {
            events = new ArrayList<>();
        }

        events.add(event);
    }

    public List<DomainEvent> getEvents() {
        if (events == null) {
            events = new ArrayList<>();
        }

        return List.copyOf(events);
    }

    public void clearEvents() {
        if (events != null) {
            events.clear();
        }
    }

    // ==================== 内部方法 ====================

    private void requireStatus(
            OrderStatus expected,
            String message) {

        if (status != expected) {
            throw new IllegalStateException(
                    message + "，当前状态：" + status
            );
        }
    }

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
