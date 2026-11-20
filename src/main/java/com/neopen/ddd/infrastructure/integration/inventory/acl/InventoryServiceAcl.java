package com.neopen.ddd.infrastructure.integration.inventory.acl;

import com.neopen.ddd.infrastructure.integration.inventory.InventoryClient;
import com.neopen.ddd.infrastructure.integration.inventory.dto.InventoryRequestDTO;
import com.neopen.ddd.infrastructure.integration.inventory.dto.InventoryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 库存防腐层 (Anti-Corruption Layer)。
 *
 * <p>分层职责：位于应用层与外部库存服务之间，将外部协议、DTO 与异常转换为
 * 领域可理解的布尔结果，业务层不感知 Feign 类型。</p>
 *
 * <p>关键协作对象：{@link InventoryClient} 执行远程调用，
 * {@link InventoryClientFallbackFactory} 在服务不可用时提供降级 (Fallback)。</p>
 *
 * <p>异常转换约定：所有远程异常与降级结果统一收敛为 false，不向上层抛出，
 * 由调用方决定是否终止下单或发起补偿。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryServiceAcl {

    /** 库存服务远程客户端，由 Spring 通过构造器注入。 */
    private final InventoryClient inventoryClient;

    /**
     * 预占库存。
     *
     * @param productId 商品标识
     * @param quantity 预占数量
     * @return true 表示库存已成功锁定；false 表示服务异常、降级或库存不足
     */
    public boolean reserveStock(Long productId, int quantity) {
        return execute("预占库存", productId, quantity, true);
    }

    /**
     * 释放已预占库存，用于订单创建失败或取消时的补偿。
     *
     * @param productId 商品标识
     * @param quantity 释放数量
     * @return true 表示释放成功；false 表示服务异常或降级，需由补偿机制重试
     */
    public boolean releaseStock(Long productId, int quantity) {
        return execute("释放库存", productId, quantity, false);
    }

    /**
     * 统一执行库存操作，屏蔽远程调用细节。
     *
     * @param action 操作名称，仅用于日志区分预占与释放
     * @param productId 商品标识
     * @param quantity 操作数量
     * @param reserve true 调用预占接口，false 调用释放接口
     * @return 远程调用成功且响应标记成功时为 true；任何异常均返回 false
     */
    private boolean execute(String action, Long productId, int quantity, boolean reserve) {
        try {
            InventoryRequestDTO request = new InventoryRequestDTO(productId, quantity);
            InventoryResponse response = reserve
                    ? inventoryClient.reserve(request)
                    : inventoryClient.release(request);
            // 响应为空或未标记成功，均视为本次库存操作失败
            return response != null && response.isSuccess();
        } catch (Exception ex) {
            log.error("库存服务调用失败: action={}, productId={}, quantity={}",
                    action, productId, quantity, ex);
            // 将远程异常转换为布尔结果，避免 Feign 异常泄漏到业务层
            return false;
        }
    }
}
