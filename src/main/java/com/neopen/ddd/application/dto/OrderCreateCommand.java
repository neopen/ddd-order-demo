package com.neopen.ddd.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderCreateCommand {
    @NotNull(message = "客户ID不能为空")
    @Min(value = 1, message = "客户ID必须大于0")
    private Long customerId;

    @NotNull(message = "商品ID不能为空")
    @Min(value = 1, message = "商品ID必须大于0")
    private Long productId;

    @NotBlank(message = "商品名称不能为空")
    private String productName;

    @Min(value = 1, message = "购买数量至少为1")
    private int quantity;

    @NotNull(message = "商品价格不能为空")
    @DecimalMin(value = "0.01", message = "商品价格必须大于0")
    private BigDecimal price;
}
