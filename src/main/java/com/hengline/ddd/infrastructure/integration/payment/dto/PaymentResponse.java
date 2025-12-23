package com.hengline.ddd.infrastructure.integration.payment.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentResponse {
    private boolean success;
    private String paymentId;
    private String status;
    private String message;
    private String transactionId;
}