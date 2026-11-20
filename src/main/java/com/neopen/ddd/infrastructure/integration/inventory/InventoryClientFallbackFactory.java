package com.neopen.ddd.infrastructure.integration.inventory;

import com.neopen.ddd.infrastructure.integration.inventory.dto.InventoryRequestDTO;
import com.neopen.ddd.infrastructure.integration.inventory.dto.InventoryResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 库存服务降级工厂：库存服务超时、不可用或熔断 (Circuit Breaker) 时接管调用。
 *
 * <p>分层职责：基础设施层的容错实现，通过 {@code fallbackFactory} 与
 * {@link InventoryClient} 绑定，业务代码无需感知。</p>
 *
 * <p>降级语义：返回 success=false、status=FAILED 的响应，表示"本次库存操作未成功"，
 * 而非"库存不足"；应用层据此终止下单或触发回滚。</p>
 */
@Component
@Slf4j
public class InventoryClientFallbackFactory implements FallbackFactory<InventoryClient> {

    /**
     * 为失败的库存调用创建降级实现。
     *
     * <p>降级实现不复用原请求的副作用，只返回失败结果，保证不产生虚假的库存扣减。</p>
     *
     * @param cause 触发降级的原始异常，用于日志与失败原因透出
     * @return 始终返回失败结果的 InventoryClient 代理
     */
    @Override
    public InventoryClient create(Throwable cause) {
        log.error("调用库存服务失败，异常信息: {}", cause.getMessage(), cause);

        return new InventoryClient() {
            /**
             * 预占库存的降级实现，返回失败结果且不改变库存状态。
             *
             * @param request 原始预占请求
             * @return success=false 的响应，message 中携带失败原因
             */
            @Override
            public InventoryResponse reserve(InventoryRequestDTO request) {
                log.error("库存预占降级，productId: {}, quantity: {}",
                        request.getProductId(), request.getQuantity());
                return InventoryResponse.builder()
                        .success(false)
                        .message("库存服务暂时不可用: " + cause.getMessage())
                        .status("FAILED")
                        .build();
            }

            /**
             * 确认预占的降级实现，返回失败结果以便对账或重试。
             *
             * @param reservationId 预占单号
             * @return success=false 的响应
             */
            @Override
            public InventoryResponse confirm(String reservationId) {
                log.error("库存确认降级，reservationId: {}", reservationId);
                return InventoryResponse.builder()
                        .success(false)
                        .message("库存服务暂时不可用: " + cause.getMessage())
                        .status("FAILED")
                        .build();
            }

            /**
             * 释放预占的降级实现，返回失败结果以便补偿任务后续重试。
             *
             * @param reservationId 预占单号
             * @return success=false 的响应
             */
            @Override
            public InventoryResponse release(String reservationId) {
                log.error("库存释放降级，reservationId: {}", reservationId);
                return InventoryResponse.builder()
                        .success(false)
                        .message("库存服务暂时不可用: " + cause.getMessage())
                        .status("FAILED")
                        .build();
            }

            /**
             * 库存查询的降级实现。
             *
             * @param productId 商品标识
             * @return success=false 的响应
             */
            @Override
            public InventoryResponse stock(Long productId) {
                return InventoryResponse.builder()
                        .success(false)
                        .message("库存服务暂时不可用: " + cause.getMessage())
                        .status("FAILED")
                        .build();
            }
        };
    }
}
