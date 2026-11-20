package com.neopen.ddd.application.dto;

import com.neopen.ddd.domain.model.ProductItem;

/**
 * 订单项视图。
 * <p>读侧输出模型，描述单个商品的购买明细，金额单位为元并携带币种。</p>
 *
 * @param id          订单项主键，持久化后生成
 * @param productId   商品标识
 * @param productName 商品名称，下单时的快照
 * @param quantity    购买数量，单位：件
 * @param unitPrice   商品单价，金额与币种
 * @param subtotal    小计，等于单价乘以数量
 */
public record OrderItemView(
        Long id,
        Long productId,
        String productName,
        int quantity,
        MoneyView unitPrice,
        MoneyView subtotal) {

    /**
     * 由订单项实体构建视图。
     *
     * @param item 订单项实体，非空
     * @return 订单项视图
     */
    public static OrderItemView from(ProductItem item) {
        return new OrderItemView(
                item.getId(),
                item.getProductId(),
                item.getProductName(),
                item.getQuantity(),
                MoneyView.from(item.getUnitPrice()),
                MoneyView.from(item.getSubtotal()));
    }
}
