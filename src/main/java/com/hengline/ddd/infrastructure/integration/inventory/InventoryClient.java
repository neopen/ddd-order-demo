package com.hengline.ddd.infrastructure.integration.inventory;

import com.hengline.ddd.infrastructure.integration.inventory.dto.InventoryRequestDTO;
import com.hengline.ddd.infrastructure.integration.inventory.dto.InventoryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 库存服务调用接口
 */
@FeignClient(
        name = "inventory-client",
        url = "${inventory.service.url:http://localhost:8080}",
        fallbackFactory = InventoryClientFallbackFactory.class)
public interface InventoryClient {
    @PostMapping("/api/inventory/reserve")
    InventoryResponse reserve(@RequestBody InventoryRequestDTO request);
}