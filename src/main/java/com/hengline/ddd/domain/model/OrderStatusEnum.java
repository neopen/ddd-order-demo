package com.hengline.ddd.domain.model;

/**
 * 订单状态枚举
 * 定义了订单在其生命周期中可能的所有状态
 */
public enum OrderStatusEnum {
    // 待支付
    WAIT_PAYMENT,
    // 待发货
    WAIT_DELIVER,
    // 待收货
    WAIT_RECEIVE,
    // 完成
    FINISH,
}
