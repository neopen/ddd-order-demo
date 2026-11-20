package com.neopen.ddd.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 创建订单命令。
 * <p>属于命令对象 (Command)：由 HTTP 请求体反序列化，经 Bean Validation 校验后
 * 传入应用服务。只承载输入数据，不包含任何业务行为。</p>
 * <p>命令不传币种，应用层统一按默认币种构建金额值对象。</p>
 */
@Data
public class OrderCreateCommand {

    /** 下单客户标识，来源请求体；必填且必须大于 0。 */
    @NotNull(message = "客户ID不能为空")
    @Min(value = 1, message = "客户ID必须大于0")
    private Long customerId;

    /** 订单项列表，来源请求体；至少包含一项，元素会递归执行字段校验。 */
    @Valid
    @NotEmpty(message = "订单项不能为空")
    private List<OrderItemCommand> items;
}
