package com.neopen.ddd.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 订单项实体 (Entity)，属于订单聚合 (Order Aggregate)。
 * <p>生命周期由聚合根 {@link Order} 统一管理：只能通过 {@code Order.create} 创建、
 * 通过 {@code addOrderItem}/{@code removeOrderItem} 增删，外部不应单独持久化。
 * 单价与小计均使用 {@link Money} 值对象，避免金额与币种分离。</p>
 * <p>不变量：数量必须为正，小计恒等于单价 × 数量；是否允许变更由订单状态决定。</p>
 */
@Entity
@Table(name = "tab_order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductItem {

    /**
     * 数据库主键，由数据库自增生成，业务上不承载含义。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 商品 ID，以标识引用商品上下文中的商品，不建立跨聚合对象引用。
     */
    @Column(name = "product_id", nullable = false)
    private Long productId;

    /**
     * 商品名称快照，下单时固化，后续商品改名不影响历史订单展示。
     */
    @Column(name = "product_name", nullable = false)
    private String productName;

    /**
     * 购买数量，必须大于 0，是计算小计的依据。
     */
    @Column(name = "quantity", nullable = false)
    private int quantity;

    /**
     * 商品单价，嵌入 {@link Money} 值对象；
     * 映射为 {@code unit_price}(DECIMAL(12,2)) 与 {@code unit_currency}(长度 8)，
     * 两列共同表达金额与币种，精度统一为两位小数。
     */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount",
                    column = @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)),
            @AttributeOverride(name = "currency",
                    column = @Column(name = "unit_currency", nullable = false, length = 8))
    })
    private Money unitPrice;

    /**
     * 小计金额，等于单价 × 数量，嵌入 {@link Money} 值对象；
     * 映射为 {@code subtotal}(DECIMAL(12,2)) 与 {@code subtotal_currency}，
     * 冗余存储以便直接查询，由聚合根维护其正确性。
     */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount",
                    column = @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)),
            @AttributeOverride(name = "currency",
                    column = @Column(name = "subtotal_currency", nullable = false, length = 8))
    })
    private Money subtotal;

    /**
     * 所属订单，{@code @ManyToOne} 懒加载，是关联的拥有方（外键在订单项表）。
     * 仅允许聚合根在装配订单项时赋值，保证聚合边界不被外部破坏。
     */
    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    private Order order;

    /**
     * 库存预占单号 (Reservation ID)，由库存服务在预占成功时返回。
     * <p>订单只保存库存上下文中的标识，不持有库存对象，体现跨上下文按 ID 引用；
     * 支付时据此确认预占，取消时据此释放预占。为空表示该订单项没有外部预占记录，
     * 通常出现在历史数据或库存服务未启用新接口的场景。</p>
     */
    @Column(name = "reservation_id", length = 64)
    private String reservationId;

    /**
     * 记录库存服务返回的预占单号，供后续确认或释放使用。
     * <p>该方法只维护数据快照，不改变订单状态；重复写入同一预占单号是幂等的。</p>
     *
     * @param reservationId 预占单号，允许为空（表示无外部预占记录）
     */
    public void assignReservation(String reservationId) {
        this.reservationId = reservationId;
    }

    /**
     * 工厂方法：创建订单项并计算小计。
     *
     * @param productId   商品 ID，不能为空
     * @param productName 商品名称，不能为空白
     * @param quantity    购买数量，必须大于 0
     * @param unitPrice   商品单价，不能为空且金额不能为负（由 {@link Money} 保证）
     * @return 已完成小计计算的订单项
     * @throws IllegalArgumentException 任一参数不合法时抛出
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
        if (unitPrice == null) {
            throw new IllegalArgumentException("商品单价不能为空");
        }

        ProductItem item = new ProductItem();
        item.productId = productId;
        item.productName = productName;
        item.quantity = quantity;
        item.unitPrice = unitPrice;
        item.subtotal = unitPrice.multiply(java.math.BigDecimal.valueOf(quantity));
        return item;
    }

    /**
     * 修改购买数量并同步重算小计。
     * <p>仅由聚合根在待支付状态下调用，保证小计与数量始终一致。</p>
     *
     * @param newQuantity 新数量，必须大于 0
     * @throws IllegalArgumentException 数量不大于 0 时抛出
     */
    public void updateQuantity(int newQuantity) {
        if (newQuantity <= 0) {
            throw new IllegalArgumentException("商品数量必须大于0");
        }
        this.quantity = newQuantity;
        recalculateSubtotal();
    }

    /**
     * 按当前单价与数量重算小计，保证冗余存储的小计不会与明细脱节。
     */
    private void recalculateSubtotal() {
        this.subtotal = this.unitPrice.multiply(java.math.BigDecimal.valueOf(this.quantity));
    }
}
