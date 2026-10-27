package com.neopen.ddd.infrastructure.integration.inventory;

import lombok.extern.slf4j.Slf4j;
import com.neopen.ddd.infrastructure.integration.inventory.dto.InventoryResponse;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

// InventoryClientFallbackFactory.java
@Component
@Slf4j
public class InventoryClientFallbackFactory implements FallbackFactory<InventoryClient> {

    @Override
    public InventoryClient create(Throwable cause) {
        try {
            return request -> {
                log.error("调用库存服务失败，productId: {}, quantity: {}, 异常信息: {}",
                        request.getProductId(), request.getQuantity(), cause.getMessage(), cause);

                // 返回降级处理结果
                return InventoryResponse.builder()
                        .success(false)
                        .message("库存服务暂时不可用: " + cause.getMessage())
                        .status("FAILED")
                        .build();
            };
        } catch (Exception e) {
            log.error("调用库存服务失败，异常信息: {}", e.getMessage(), e);
            return null;
        }
    }
}
