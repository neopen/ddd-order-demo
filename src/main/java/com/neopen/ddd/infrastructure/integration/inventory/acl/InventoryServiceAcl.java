package com.neopen.ddd.infrastructure.integration.inventory.acl;

import com.neopen.ddd.infrastructure.integration.inventory.InventoryClient;
import com.neopen.ddd.infrastructure.integration.inventory.dto.InventoryRequestDTO;
import com.neopen.ddd.infrastructure.integration.inventory.dto.InventoryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 库存防腐层 (Anti-Corruption Layer)。
 *
 * <p>分层职责：位于应用层与外部库存服务之间，将外部协议、DTO 与异常转换为
 * 领域可理解的结果，业务层不感知 Feign 类型，也不知道库存侧的预占单结构。</p>
 *
 * <p>预占语义：{@link #reserveStock} 返回预占单号而不是布尔值，表示库存被“锁定一段时间”；
 * 订单支付后由 {@link #confirmStock} 确认，取消后由 {@link #releaseStock} 释放，
 * 未确认的预占在 TTL 到期后由库存服务自动回收。该模型让跨服务的库存占用具备
 * 明确生命周期，避免永久扣减造成的悬挂库存。</p>
 *
 * <p>异常转换约定：所有远程异常与降级结果统一收敛为失败（null 或 false），不向上层抛出，
 * 由调用方决定是否终止下单或发起补偿。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryServiceAcl {

    /** 库存服务远程客户端，由 Spring 通过构造器注入。 */
    private final InventoryClient inventoryClient;

    /** 预占有效期（秒），由订单服务下发，库存服务到期未确认则自动释放。 */
    @Value("${app.inventory.reservation-ttl-seconds:900}")
    private int reservationTtlSeconds;

    /**
     * 预占库存。
     *
     * @param productId 商品标识
     * @param quantity  预占数量
     * @param orderNum  订单号，写入预占单便于库存侧对账
     * @return 预占单号；服务异常、降级或库存不足时返回 {@code null}
     */
    public String reserveStock(Long productId, int quantity, String orderNum) {
        InventoryRequestDTO request = InventoryRequestDTO.reserve(
                productId, quantity, orderNum, reservationTtlSeconds);
        InventoryResponse response = execute("预占库存", () -> inventoryClient.reserve(request),
                productId, quantity);
        return response == null ? null : response.getReservationId();
    }

    /**
     * 确认预占，通常在订单支付成功后调用，表示库存被真正消耗。
     *
     * @param reservationId 预占单号
     * @return true 表示预占已确认；false 表示服务异常或降级，需要后续对账
     */
    public boolean confirmStock(String reservationId) {
        InventoryResponse response = execute("确认预占",
                () -> inventoryClient.confirm(reservationId), null, 0);
        return response != null && response.isSuccess();
    }

    /**
     * 释放预占，用于订单创建失败、取消或关闭时的补偿。
     *
     * @param reservationId 预占单号
     * @return true 表示释放成功；false 表示服务异常或降级，需由补偿机制重试
     */
    public boolean releaseStock(String reservationId) {
        InventoryResponse response = execute("释放预占",
                () -> inventoryClient.release(reservationId), null, 0);
        return response != null && response.isSuccess();
    }

    /**
     * 统一执行库存操作，屏蔽远程调用细节。
     *
     * @param action    操作名称，仅用于日志区分预占、确认与释放
     * @param call      实际远程调用
     * @param productId 商品标识，仅用于日志，可为 null
     * @param quantity  操作数量，仅用于日志
     * @return 远程响应；任何异常均返回 {@code null}，由调用方视为失败
     */
    private InventoryResponse execute(String action, RemoteCall call,
                                      Long productId, int quantity) {
        try {
            InventoryResponse response = call.invoke();
            if (response == null || !response.isSuccess()) {
                log.warn("库存{}未成功: productId={}, quantity={}, status={}, message={}",
                        action, productId, quantity,
                        response == null ? null : response.getStatus(),
                        response == null ? null : response.getMessage());
            }
            return response;
        } catch (Exception ex) {
            log.error("库存服务调用失败: action={}, productId={}, quantity={}",
                    action, productId, quantity, ex);
            // 将远程异常转换为失败结果，避免 Feign 异常泄漏到业务层
            return null;
        }
    }

    /**
     * 远程调用函数式接口，便于把不同签名的方法传入统一的异常收敛逻辑。
     */
    @FunctionalInterface
    private interface RemoteCall {
        InventoryResponse invoke();
    }
}
