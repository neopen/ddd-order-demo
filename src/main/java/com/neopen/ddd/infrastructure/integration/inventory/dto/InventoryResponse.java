package com.neopen.ddd.infrastructure.integration.inventory.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InventoryResponse {
    private boolean success;
    private String status;
    private String message;
    private Object data;


    public static InventoryResponse of(boolean success, String status, String message, Object data) {
        return InventoryResponse.builder()
                .success(success)
                .status(status)
                .message(message)
                .data(data)
                .build();
    }

    public static InventoryResponse success() {
        return of(true, "success", "Inventory service is available", null);
    }

    public static InventoryResponse fail() {
        return of(false, "fail", "Inventory service is unavailable", null);
    }

    public static InventoryResponse fail(String message) {
        return of(false, "fail", message, null);
    }

}