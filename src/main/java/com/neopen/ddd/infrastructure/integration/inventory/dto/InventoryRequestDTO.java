package com.neopen.ddd.infrastructure.integration.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 库存服务请求参数（数据传输对象，DTO）。
 *
 * <p>字段与库存服务的 HTTP 契约一一对应，仅在基础设施层内部流转，不进入领域模型。</p>
 *
 * <p>预占请求使用 {@code productId + quantity + orderNum + ttlSeconds}；
 * 释放与确认请求使用 {@code reservationId} 定位预占单，两者共用同一个 DTO，
 * 未使用的字段为 {@code null}。</p>
 *
 * <p>无参构造为 Jackson 反序列化所必需；静态工厂便于防腐层按场景快速组装请求。</p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class InventoryRequestDTO {

    /** 商品标识。 */
    private Long productId;

    /** 操作数量，预占与释放时均表示库存件数。 */
    private int quantity;

    /** 订单号，作为预占单的业务归属，便于库存侧对账与排查。 */
    private String orderNum;

    /** 预占有效期（秒），到期后库存服务自动释放；为空时由库存服务使用默认值。 */
    private Integer ttlSeconds;

    /** 预占单号，确认或释放库存时用于定位预占聚合。 */
    private String reservationId;

    /**
     * 构造预占库存请求。
     *
     * @param productId  商品标识
     * @param quantity   预占数量
     * @param orderNum   订单号
     * @param ttlSeconds 预占有效期（秒）
     * @return 预占请求
     */
    public static InventoryRequestDTO reserve(Long productId, int quantity,
                                              String orderNum, Integer ttlSeconds) {
        return new InventoryRequestDTO(productId, quantity, orderNum, ttlSeconds, null);
    }
}
