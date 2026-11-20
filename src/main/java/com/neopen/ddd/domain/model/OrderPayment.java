package com.neopen.ddd.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 订单支付记录实体 (Entity)，属于订单聚合 (Order Aggregate)。
 * <p>保存支付渠道回执的关键信息与支付金额快照；与订单一对一，
 * 由聚合根的 {@code recordPayment} 创建并维护，随订单级联持久化。</p>
 * <p>不变量：一个订单最多一条支付记录；金额在创建时从订单应付金额快照而来，
 * 不随订单金额后续变化而改变，便于对账与审计。</p>
 */
@Entity
@Table(name = "tab_order_payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderPayment {

    /**
     * 数据库主键，自增生成，业务上不承载含义。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 支付单号，由支付渠道或本地支付场景生成，用于对账与幂等控制。
     */
    private String paymentId;

    /**
     * 支付渠道交易流水号，用于与第三方支付平台核对资金。
     */
    private String transactionId;

    /**
     * 支付渠道返回的支付状态文本（如 COMPLETED）；
     * 使用字符串而非枚举，便于兼容渠道状态扩展。
     */
    private String status;

    /**
     * 支付完成时间，记录渠道回执时刻，用于审计与对账。
     */
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    /**
     * 支付金额快照，嵌入 {@link Money} 值对象；
     * 映射为 {@code amount}(DECIMAL(12,2)) 与 {@code currency}(长度 8)，
     * 币种与精度与订单应付金额保持一致。
     */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount",
                    column = @Column(name = "amount", nullable = false, precision = 12, scale = 2)),
            @AttributeOverride(name = "currency",
                    column = @Column(name = "currency", nullable = false, length = 8))
    })
    private Money amount;

    /**
     * 关联的订单，{@code @OneToOne} 的反向端（mappedBy = "orderPayment"），懒加载。
     * 唯一外键维护在订单表，由聚合根在记录支付时赋值。
     */
    @Setter
    @OneToOne(mappedBy = "orderPayment", fetch = FetchType.LAZY)
    private Order order;

    /**
     * 根据订单创建支付记录，并快照订单应付金额与当前时间。
     * <p>由聚合根调用，保证支付记录金额与订单应付金额一致。</p>
     *
     * @param paymentId     支付单号
     * @param transactionId 渠道交易流水号
     * @param status        渠道返回的支付状态
     * @param order         所属订单，其应付金额将作为支付金额快照
     */
    public OrderPayment(String paymentId, String transactionId, String status, Order order) {
        this.paymentId = paymentId;
        this.transactionId = transactionId;
        this.status = status;
        this.paidAt = LocalDateTime.now();
        this.amount = order.getPayableAmount();
        this.order = order;
    }
}
