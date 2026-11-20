package com.neopen.ddd.infrastructure.integration.inventory;

import com.neopen.ddd.infrastructure.integration.inventory.dto.InventoryRequestDTO;
import com.neopen.ddd.infrastructure.integration.inventory.dto.InventoryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 库存服务的 Feign 客户端声明。
 *
 * <p>分层职责：基础设施层的外部集成边界，只描述与库存服务之间的 HTTP 契约，
 * 不承担业务判断；由防腐层 (Anti-Corruption Layer) 封装后供应用层使用。</p>
 *
 * <p>预占语义：库存服务不再直接扣减永久库存，而是生成带 TTL 的预占单
 * (Reservation)，订单支付后确认、取消后释放、超时由库存服务自动回收。
 * 这样跨服务的库存占用具备明确的生命周期，避免“扣了库存但订单丢失”的悬挂状态。</p>
 *
 * <p>关键协作对象：{@link InventoryClientFallbackFactory} 提供服务不可用时的
 * 降级 (Fallback)，熔断 (Circuit Breaker) 打开后请求直接进入降级逻辑。</p>
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
     * 预占库存，对应库存服务 POST /api/inventory/reservations。
     *
     * @param request 预占请求，含商品、数量、订单号与 TTL
     * @return 预占结果，成功时携带预占单号；降级时 success 为 false
     */
    @PostMapping("/api/inventory/reservations")
    InventoryResponse reserve(@RequestBody InventoryRequestDTO request);

    /**
     * 确认预占，对应库存服务 POST /api/inventory/reservations/{id}/confirm。
     * <p>订单支付成功后调用，把预占单从 PENDING 迁移为 CONFIRMED。</p>
     *
     * @param reservationId 预占单号
     * @return 确认结果；降级时 success 为 false
     */
    @PostMapping("/api/inventory/reservations/{reservationId}/confirm")
    InventoryResponse confirm(@PathVariable("reservationId") String reservationId);

    /**
     * 释放预占，对应库存服务 POST /api/inventory/reservations/{id}/release。
     * <p>订单取消、关闭或创建失败时调用，把库存归还给可售池。</p>
     *
     * @param reservationId 预占单号
     * @return 释放结果；降级时 success 为 false
     */
    @PostMapping("/api/inventory/reservations/{reservationId}/release")
    InventoryResponse release(@PathVariable("reservationId") String reservationId);

    /**
     * 查询商品当前可售库存，对应库存服务 GET /api/inventory/{productId}。
     *
     * @param productId 商品标识
     * @return 查询结果，data 中为剩余库存
     */
    @GetMapping("/api/inventory/{productId}")
    InventoryResponse stock(@PathVariable("productId") Long productId);
}
