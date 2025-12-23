package com.hengline.ddd.domain.statemachine;

/**
 * 订单事件枚举
 *
 * @author <a href="mailto:<EMAIL>">pleuvoir</a>
 */
public enum OrderEventEnum {
    //支付
    PAYED,
    //发货
    DELIVERY,
    //收货
    RECEIVED,

    /**
     * 订单取消
     */
    CANCELLED,

    ;
}