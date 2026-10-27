package com.neopen.ddd.domain.model;

/**
 * 订单事件枚举
 *
 * @author <a href="mailto:<EMAIL>">pleuvoir</a>
 */
public enum OrderEvent {
    CREATE,         // 创建订单
    PAY,            // 支付成功
    CANCEL,         // 取消订单
    SHIP,           // 发货
    SIGN,           // 确认签收
    COMPLETE,       // 确认完成
    REFUND,         // 申请退款
    REFUND_SUCCESS, // 退款成功
    REFUND_FAILED,  // 退款失败
    CLOSE           // 异常关闭

    ;
}