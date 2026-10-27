package com.neopen.ddd.application.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class OrderCreateCommand {
    private Long customerId;
    private Long productId;
    private String productName;
    @Min(1)
    private int quantity;
    private String price;
}