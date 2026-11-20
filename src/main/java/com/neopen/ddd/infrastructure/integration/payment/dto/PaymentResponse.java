package com.neopen.ddd.infrastructure.integration.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 支付服务响应结果（数据传输对象，DTO）。
 *
 * <p>用于判断支付是否成功并保留支付服务返回的追踪信息；无参构造供 Jackson
 * 反序列化，Builder 便于降级时构造失败结果。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    /** 是否成功，业务层最终依据此字段判断支付结果。 */
    private boolean success;

    /** 支付单号，支付服务内部标识。 */
    private String paymentId;

    /** 状态标识，如 success / fail / FAILED。 */
    private String status;

    /** 描述信息，失败时用于日志与提示。 */
    private String message;

    /** 交易流水号，用于对账与问题排查。 */
    private String transactionId;
}