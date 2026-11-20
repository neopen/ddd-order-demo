package com.neopen.ddd.infrastructure.integration.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 支付服务请求参数（数据传输对象，DTO）。
 *
 * <p>字段与支付服务的 HTTP 契约一一对应，由防腐层从订单聚合转换而来，
 * 不进入领域模型。</p>
 *
 * <p>无参构造为 Jackson 反序列化所必需，全参构造便于一次性组装所有字段。</p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentRequestDTO {
    /** 订单号，支付服务侧的幂等与对账依据。 */
    private String orderNum;

    /** 客户标识，本服务以 CUSTOMER- 前缀拼接客户 ID。 */
    private String customerId;

    /** 支付金额，取订单应付金额，单位为货币主单位。 */
    private double amount;

    /** 币种代码，如 CNY。 */
    private String currency;

    /** 支付方式，如 ALIPAY。 */
    private String paymentMethod;
}