package com.neopen.ddd.infrastructure.integration.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 库存服务响应结果（数据传输对象，DTO）。
 *
 * <p>承载库存服务返回的处理状态，供防腐层转换为布尔结果；无参构造供 Jackson
 * 反序列化使用，Builder 便于降级时快速构造失败结果。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryResponse {
    /** 是否成功，库存服务与降级实现共同遵循的判定字段。 */
    private boolean success;

    /** 状态标识，如 success / fail / FAILED。 */
    private String status;

    /** 描述信息，失败时用于日志与提示。 */
    private String message;

    /** 附加数据，结构由库存服务定义，本服务不解析。 */
    private Object data;


    /**
     * 按给定字段构造响应。
     *
     * @param success 是否成功
     * @param status 状态标识
     * @param message 描述信息
     * @param data 附加数据，可为 null
     * @return 新的响应对象
     */
    public static InventoryResponse of(boolean success, String status, String message, Object data) {
        return InventoryResponse.builder()
                .success(success)
                .status(status)
                .message(message)
                .data(data)
                .build();
    }

    /**
     * 构造成功响应，用于本地模拟或测试场景。
     *
     * @return success=true 的响应
     */
    public static InventoryResponse success() {
        return of(true, "success", "Inventory service is available", null);
    }

    /**
     * 构造通用失败响应。
     *
     * @return success=false 的响应
     */
    public static InventoryResponse fail() {
        return of(false, "fail", "Inventory service is unavailable", null);
    }

    /**
     * 构造带自定义原因的失败响应。
     *
     * @param message 失败原因描述
     * @return success=false 的响应
     */
    public static InventoryResponse fail(String message) {
        return of(false, "fail", message, null);
    }

}