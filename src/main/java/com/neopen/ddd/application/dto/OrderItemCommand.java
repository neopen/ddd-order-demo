package com.neopen.ddd.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 创建订单时的单个商品项。
 * <p>作为创建订单命令的嵌套元素由请求体反序列化；单价金额单位为元，
 * 币种不在此声明，由应用层按默认币种补齐。</p>
 */
@Data
public class OrderItemCommand {

    /** 商品标识，来源请求体；必填且必须大于 0。 */
    @NotNull(message = "商品ID不能为空")
    @Min(value = 1, message = "商品ID必须大于0")
    private Long productId;

    /** 商品名称，来源请求体；展示用，不能为空白。 */
    @NotBlank(message = "商品名称不能为空")
    private String productName;

    /** 购买数量，单位：件；至少为 1，int 默认值 0 会被校验拦截。 */
    @Min(value = 1, message = "购买数量至少为1")
    private int quantity;

    /** 商品单价，单位：元，精度两位小数；来源请求体，必须大于 0。 */
    @NotNull(message = "商品价格不能为空")
    @DecimalMin(value = "0.01", message = "商品价格必须大于0")
    private BigDecimal price;
}
