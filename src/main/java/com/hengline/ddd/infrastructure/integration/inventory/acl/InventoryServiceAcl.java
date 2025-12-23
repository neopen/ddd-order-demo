package com.hengline.ddd.infrastructure.integration.inventory.acl;

import com.hengline.ddd.infrastructure.integration.inventory.InventoryClient;
import com.hengline.ddd.infrastructure.integration.inventory.dto.InventoryRequestDTO;
import com.hengline.ddd.infrastructure.integration.inventory.dto.InventoryResponse;
import org.springframework.stereotype.Component;

@Component
public class InventoryServiceAcl {

    private final InventoryClient inventoryClient;

    public InventoryServiceAcl(InventoryClient inventoryClient) {
        this.inventoryClient = inventoryClient;
    }

    public boolean reserveStock(Long productId, int quantity) {
        try {
            InventoryRequestDTO request = new InventoryRequestDTO(productId, quantity);
            InventoryResponse response = inventoryClient.reserve(request);
            return response.isSuccess();
        } catch (Exception e) {
            System.err.println("库存服务调用失败: " + e.getMessage());
            return false;
        }
    }
}