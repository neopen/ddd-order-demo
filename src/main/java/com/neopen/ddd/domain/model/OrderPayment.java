package com.neopen.ddd.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "tab_order_payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Data
public class OrderPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String paymentId;
    private String transactionId;
    private String status;
    private LocalDateTime paidAt;
    private Double amount;

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private Order order;

    public OrderPayment(String paymentId, String transactionId, String status, Order order) {
        this.paymentId = paymentId;
        this.transactionId = transactionId;
        this.status = status;
        this.paidAt = java.time.LocalDateTime.now();
        this.amount = order.getTotalAmount().doubleValue();
        this.order = order;
    }
}