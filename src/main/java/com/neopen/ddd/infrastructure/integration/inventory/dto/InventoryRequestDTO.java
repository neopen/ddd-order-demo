package com.neopen.ddd.infrastructure.integration.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 库存服务请求参数（数据传输对象，DTO）。
 *
 * <p>字段与库存服务的 HTTP 契约一一对应，仅在基础设施层内部流转，不进入领域模型。</p>
 *
 * <p>无参构造为 Jackson 反序列化所必需；全参构造便于防腐层快速组装请求。</p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class InventoryRequestDTO {
    /** 商品标识。 */
    private Long productId;

    /** 操作数量，预占与释放时均表示库存件数。 */
    private int quantity;
}
