package com.neopen.ddd.infrastructure.integration.inventory;

import com.neopen.ddd.infrastructure.integration.inventory.dto.InventoryRequestDTO;
import com.neopen.ddd.infrastructure.integration.inventory.dto.InventoryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 库存服务的 Feign 客户端声明。
 *
 * <p>分层职责：基础设施层的外部集成边界，只描述与库存服务之间的 HTTP 契约，
 * 不承担业务判断；由防腐层 (Anti-Corruption Layer) 封装后供应用层使用。</p>
 *
 * <p>关键协作对象：{@link InventoryClientFallbackFactory} 提供服务不可用时的
 * 降级 (Fallback)，熔断 (Circuit Breaker) 打开后请求直接进入降级逻辑。</p>
 *
 * <p>配置来源：目标地址取自 {@code inventory.service.url}，默认
 * {@code http://localhost:9001}；连接超时 2 秒、读取超时 3 秒配置在 application.yml 的
 * {@code spring.cloud.openfeign.client.config.inventory-client} 下；熔断阈值由
 * {@code resilience4j.circuitbreaker.configs.default} 控制。</p>
 *
 * <p>异常转换约定：远程异常由降级工厂吸收为 success=false 的响应，
 * 不向调用方抛出 Feign 异常。</p>
 */
@FeignClient(
        name = "inventory-client",
        url = "${inventory.service.url:http://localhost:9001}",
        fallbackFactory = InventoryClientFallbackFactory.class)
public interface InventoryClient {

    /**
     * 预占库存，对应库存服务 POST /api/inventory/reserve。
     *
     * @param request 预占请求，含商品标识与数量
     * @return 库存服务处理结果；降级时 success 为 false
     */
    @PostMapping("/api/inventory/reserve")
    InventoryResponse reserve(@RequestBody InventoryRequestDTO request);

    /**
     * 释放已预占库存，对应库存服务 POST /api/inventory/release。
     *
     * @param request 释放请求，含商品标识与数量
     * @return 库存服务处理结果；降级时 success 为 false
     */
    @PostMapping("/api/inventory/release")
    InventoryResponse release(@RequestBody InventoryRequestDTO request);
}
