package com.hengline.ddd.infrastructure.integration.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentRequestDTO {
    private String orderNum;
    private String customerId;
    private double amount;
    private String currency;
    private String paymentMethod;
}