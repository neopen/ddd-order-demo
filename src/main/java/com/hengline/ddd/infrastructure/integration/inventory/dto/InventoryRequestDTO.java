package com.hengline.ddd.infrastructure.integration.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class InventoryRequestDTO {
    private Long productId;
    private int quantity;
}