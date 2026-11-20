package com.neopen.ddd.domain.model;

import cn.hutool.core.util.RandomUtil;
import com.neopen.ddd.domain.event.OrderCreatedEvent;
import com.neopen.ddd.domain.event.OrderStatusChangedEvent;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 订单实体，同时是订单聚合 (Aggregate) 的聚合根 (Aggregate Root)。
 *
 * <p>聚合边界内包含订单项 {@link ProductItem} 与支付记录 {@link OrderPayment}，
 * 外部只能通过本类修改聚合内部数据，由聚合根整体维护不变量。</p>
 * <p>职责：维护订单状态、订单项、金额与优惠等领域数据，并在内部登记领域事件 (Domain Event)。
 * 状态迁移规则由状态机 (State Machine) 判断，聚合根只负责应用迁移结果并维护
 * 退款前状态等内部不变量，不再重复维护一份状态迁移表。</p>
 * <p>协作对象：应用服务负责事务边界与持久化，状态机服务负责计算目标状态，
 * 事件发布器在事务提交后发布本类登记的领域事件。</p>
 * <p>事务/并发：使用 JPA {@code @Version} 乐观锁防止并发覆盖；实例不是线程安全的，
 * 应在单个事务内完成读取、修改和事件拉取。参数或状态不合法时抛出
 * {@link IllegalArgumentException} 或 {@link IllegalStateException}，由上层转为业务错误。</p>
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
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends AggregateRoot {

    /**
     * 数据库主键，自增生成，业务上不承载含义。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 订单号，业务唯一标识；创建后不可修改（{@code updatable = false}）。
     * <p>由时间戳加随机串生成，唯一的数据库约束 {@code uk_orders_order_num} 作为最终兜底。</p>
     */
    @Column(name = "order_num", nullable = false, updatable = false, length = 64)
    private String orderNum;

    /**
     * 下单客户 ID，引用客户上下文中的客户，只保存标识而非对象引用。
     */
    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    /**
     * 当前订单状态，以枚举名字符串持久化，便于数据库直接阅读。
     * <p>只能经由 {@code transitTo} 在状态机校验后变更，外部不能直接赋值。</p>
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    /**
     * 退款前状态，用于退款失败后恢复；非退款流程中为空。
     * <p>进入退款中时写入原状态，退款成功或恢复成功后清空，
     * 使“退款失败回到哪一步”这一业务信息只保存在聚合内部。</p>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "pre_status", length = 32)
    private OrderStatus preStatus;

    /**
     * 优惠前订单总额。
     * <p>嵌入 {@link Money}，映射为 {@code total_amount}(DECIMAL(12,2)) 与
     * {@code total_currency}；由订单项小计累加得出，币种取首个订单项。</p>
     */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount",
                    column = @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)),
            @AttributeOverride(name = "currency",
                    column = @Column(name = "total_currency", nullable = false, length = 8))
    })
    private Money totalAmount;

    /**
     * 优惠金额。
     * <p>嵌入 {@link Money}，映射为 {@code discount_amount}(DECIMAL(12,2)) 与
     * {@code discount_currency}；无优惠时为零金额，且不应超过订单总额。</p>
     */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount",
                    column = @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)),
            @AttributeOverride(name = "currency",
                    column = @Column(name = "discount_currency", nullable = false, length = 8))
    })
    private Money discountAmount;

    /**
     * 实际应付金额 = 原始金额 - 优惠金额。
     * <p>嵌入 {@link Money}，映射为 {@code payable_amount}(DECIMAL(12,2)) 与
     * {@code payable_currency}；该不变量在创建与订单项变更时同步维护。</p>
     */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount",
                    column = @Column(name = "payable_amount", nullable = false, precision = 12, scale = 2)),
            @AttributeOverride(name = "currency",
                    column = @Column(name = "payable_currency", nullable = false, length = 8))
    })
    private Money payableAmount;

    /**
     * 命中的优惠策略编码，{@code NONE} 表示未优惠，用于展示与问题排查。
     */
    @Column(name = "discount_policy", length = 64)
    private String discountPolicy;

    /**
     * 创建时间，写入后不再更新（{@code updatable = false}），用于审计与排序。
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 最后更新时间，由聚合内部每次有效变更统一刷新。
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 订单项集合，一对多关联；级联保存/删除并启用孤儿移除。
     * <p>由 {@link ProductItem#getOrder()} 维护外键，聚合根是唯一修改入口。</p>
     */
    @OneToMany(
            mappedBy = "order",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<ProductItem> productItems = new ArrayList<>();

    /**
     * 支付记录，与订单一对一，级联持久化；订单表通过唯一外键 {@code payment_id} 关联。
     * <p>采用懒加载，仅在需要展示支付信息时访问。</p>
     */
    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "payment_id", unique = true)
    private OrderPayment orderPayment;

    /**
     * 操作人 ID，示例中创建时随机生成，生产环境应取自登录上下文。
     */
    @Column(name = "operator_id")
    private Long operatorId;

    /**
     * 是否已成功预占库存，用于取消/关闭订单时补偿释放。
     * <p>库存预占发生在订单数据库事务之外，必须靠该标记判断是否需要补偿，
     * 避免重复释放或漏释放。</p>
     */
    @Column(name = "stock_reserved", nullable = false)
    private boolean stockReserved;

    /**
     * JPA 乐观锁版本号。
     * <p>每次更新由 JPA 自动递增；并发修改会让事务回滚，
     * 避免同一订单被重复支付或重复迁移状态。</p>
     */
    @Version
    private Long version;

    // ==================== 创建订单 ====================

    /**
     * 创建订单的简化入口：按无优惠计价。
     *
     * @param customerId 下单客户 ID，不能为空
     * @param items      订单项列表，不能为空
     * @return 新建且已登记创建事件的订单
     * @throws IllegalArgumentException 参数非法时抛出
     */
    public static Order create(Long customerId, List<ProductItem> items) {
        return create(customerId, items, null);
    }

    /**
     * 工厂方法：创建处于待支付状态的订单，并登记订单创建事件。
     * <p>关键行为：生成订单号与操作人、装配订单项及所属关系、累加订单总额，
     * 再应用定价结果（无定价时按无优惠处理），最后登记 {@link OrderCreatedEvent}。
     * 事件只登记不发布，由应用层在订单持久化成功后统一发布。</p>
     *
     * @param customerId 下单客户 ID，不能为空
     * @param items      订单项列表，不能为空且至少一项，各项币种必须一致
     * @param pricing    定价结果，可为 {@code null} 表示无优惠
     * @return 新建的订单聚合根
     * @throws IllegalArgumentException 客户或订单项为空、总金额不为正、
     *                                  或定价结果与订单项金额不一致时抛出
     */
    public static Order create(Long customerId, List<ProductItem> items, OrderPricing pricing) {
        if (customerId == null) {
            throw new IllegalArgumentException("客户ID不能为空");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("订单项不能为空");
        }

        Order order = new Order();
        order.orderNum = generateOrderNumber();
        order.customerId = customerId;
        order.status = OrderStatus.CREATED;
        order.createdAt = LocalDateTime.now();
        order.updatedAt = order.createdAt;
        order.operatorId = RandomUtil.randomLong();

        for (ProductItem item : items) {
            Objects.requireNonNull(item, "订单项不能为空");
            order.productItems.add(item);
            item.setOrder(order);
        }

        order.recalculateTotalAmount();
        if (!order.getTotalAmount().isPositive()) {
            throw new IllegalArgumentException("订单总金额必须大于零");
        }

        if (pricing == null) {
            order.discountAmount = Money.zero(order.totalAmount.getCurrency());
            order.payableAmount = order.totalAmount;
            order.discountPolicy = "NONE";
        } else {
            if (!pricing.originalAmount().equals(order.totalAmount)) {
                throw new IllegalArgumentException("定价结果与订单项金额不一致");
            }
            order.discountAmount = pricing.discountAmount();
            order.payableAmount = pricing.payableAmount();
            order.discountPolicy = pricing.policyCode();
        }

        order.registerEvent(new OrderCreatedEvent(
                order.orderNum, customerId, order.payableAmount));
        return order;
    }

    // ==================== 订单项管理 ====================

    /**
     * 向待支付订单添加订单项，并重算金额、重置优惠。
     * <p>已存在的同一订单项会被忽略；添加后原有优惠失效，需重新定价。</p>
     *
     * @param item 待添加的订单项，不能为空
     * @throws IllegalStateException 订单不处于待支付状态时抛出
     * @throws NullPointerException  订单项为空时抛出
     */
    public void addOrderItem(ProductItem item) {
        if (status != OrderStatus.CREATED) {
            throw new IllegalStateException("只有待支付订单才能修改订单项");
        }
        Objects.requireNonNull(item, "订单项不能为空");
        if (productItems.contains(item)) {
            return;
        }

        productItems.add(item);
        item.setOrder(this);
        recalculateTotalAmount();
        resetPricing();
        touch();
    }

    /**
     * 从待支付订单移除订单项，并重算金额、重置优惠。
     * <p>解除订单项与订单的关联，使孤儿移除生效，避免残留无主明细。</p>
     *
     * @param item 待移除的订单项，不存在时静默忽略
     * @throws IllegalStateException 订单不处于待支付状态时抛出
     */
    public void removeOrderItem(ProductItem item) {
        if (status != OrderStatus.CREATED) {
            throw new IllegalStateException("只有待支付订单才能移除订单项");
        }
        if (productItems.remove(item)) {
            item.setOrder(null);
            recalculateTotalAmount();
            resetPricing();
            touch();
        }
    }

    /**
     * 获取订单项的只读视图，防止外部绕过聚合根直接修改集合。
     *
     * @return 不可变的订单项列表
     */
    public List<ProductItem> getProductItems() {
        return Collections.unmodifiableList(productItems);
    }

    /**
     * 按订单项小计累加重算订单总额。
     * <p>集合为空时归零，币种取首个订单项；跨币种相加会被 {@link Money} 拒绝，
     * 从而保证订单总额的币种一致性。</p>
     */
    private void recalculateTotalAmount() {
        if (productItems.isEmpty()) {
            this.totalAmount = Money.zero(Money.DEFAULT_CURRENCY);
            return;
        }
        String currency = productItems.get(0).getSubtotal().getCurrency();
        this.totalAmount = productItems.stream()
                .map(ProductItem::getSubtotal)
                .reduce(Money.zero(currency), Money::add);
    }

    /**
     * 订单项变化后重新按无优惠计价，避免优惠金额与订单项金额不一致。
     */
    private void resetPricing() {
        this.discountAmount = Money.zero(this.totalAmount.getCurrency());
        this.payableAmount = this.totalAmount;
        this.discountPolicy = "NONE";
    }

    // ==================== 支付信息 ====================

    /**
     * 记录已由支付渠道确认的支付结果，不改变订单状态。
     * <p>订单状态迁移仍必须通过状态机应用服务完成，本方法只维护
     * “一个订单最多一条支付记录”的聚合不变量，并刷新更新时间。</p>
     *
     * @param paymentInfo 支付记录，不能为空，金额应为订单应付金额的快照
     * @throws IllegalStateException 订单不是待支付状态，或已存在支付记录时抛出
     * @throws NullPointerException  支付记录为空时抛出
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

    // ==================== 库存预占状态 ====================

    /**
     * 标记库存已成功预占，供取消/关闭订单时判断是否需要补偿释放。
     *
     * @throws IllegalStateException 订单不处于待支付状态时抛出
     */
    public void markStockReserved() {
        if (status != OrderStatus.CREATED) {
            throw new IllegalStateException("只有待支付订单才能标记库存预占");
        }
        this.stockReserved = true;
        touch();
    }

    /**
     * 清除库存预占标记，通常在库存已释放或订单进入终态后调用。
     * <p>该操作幂等：重复调用只是再次置为 false 并刷新更新时间。</p>
     */
    public void releaseStockReservation() {
        this.stockReserved = false;
        touch();
    }

    // ==================== 状态机同步 ====================

    /**
     * 应用状态机已验证的迁移结果（不携带触发事件，因此不登记领域事件）。
     *
     * @param target 目标状态，不能为空
     * @throws IllegalArgumentException 目标状态为空时抛出
     * @throws IllegalStateException    违反退款相关状态约束时抛出
     */
    public void transitTo(OrderStatus target) {
        transitTo(target, null);
    }

    /**
     * 应用状态机已验证的迁移结果。
     * <p>只有状态机应用服务应调用此方法；合法迁移规则集中定义在 OrderStateMachineConfig。
     * 这里仅维护聚合内部字段不变量，不再重复维护一份状态迁移表。</p>
     * <p>不变量：进入退款中必须来自已支付/已发货/已签收，并记录退款前状态；
     * 从退款中恢复只能回到退款前状态；确认退款只能从退款中出发。
     * 状态与时间戳总是先更新，领域事件仅在订单已持久化（{@code id} 非空）时登记，
     * 避免发布无法关联聚合根的事件。</p>
     *
     * @param target 目标状态，不能为空
     * @param event  触发本次迁移的领域事件；传入后会记录领域事件供应用层发布
     * @throws IllegalArgumentException 目标状态为空时抛出
     * @throws IllegalStateException    当前状态不允许该迁移时抛出
     */
    public void transitTo(OrderStatus target, OrderEvent event) {
        Objects.requireNonNull(target, "目标状态不能为空");
        OrderStatus from = this.status;

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

        if (event != null && this.id != null) {
            registerEvent(new OrderStatusChangedEvent(this.id, from, target, event));
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 刷新最后更新时间，所有对聚合的有效变更都应调用，保证审计字段可信。
     */
    private void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 生成全局唯一订单号：时间戳保证大体有序，随机串降低同毫秒碰撞概率，
     * 数据库唯一约束是最终兜底。
     *
     * @return 形如 {@code ORD<时间戳><8位随机串>} 的订单号
     */
    private static String generateOrderNumber() {
        return "ORD"
                + System.currentTimeMillis()
                + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8);
    }
}
