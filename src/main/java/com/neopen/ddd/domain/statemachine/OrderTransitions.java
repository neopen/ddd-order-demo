package com.neopen.ddd.domain.statemachine;

import com.neopen.ddd.domain.model.OrderEvent;
import com.neopen.ddd.domain.model.OrderStatus;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * 订单状态迁移静态映射表。
 *
 * <p>以“当前状态 -> 事件 -> 目标状态”的表格形式描述合法迁移，
 * 可作为状态机配置 (State Machine Config) 的业务规则速查与轻量查询入口；
 * 本表是简化版参考规则，与状态机配置不一致时应以状态机配置为准。</p>
 *
 * <p>并发：表在静态初始化后不可修改，可被多线程安全共享读取。</p>
 */
public final class OrderTransitions {

    /** 不可变迁移表：外层按源状态索引，内层按事件索引目标状态。 */
    private static final Map<OrderStatus, Map<OrderEvent, OrderStatus>> TABLE;

    static {
        Map<OrderStatus, Map<OrderEvent, OrderStatus>> t = new EnumMap<>(OrderStatus.class);

        // 待支付：可支付、取消或异常关闭
        t.put(OrderStatus.CREATED, Map.of(
                OrderEvent.PAY,         OrderStatus.PAID,
                OrderEvent.CANCEL,      OrderStatus.CANCELLED,
                OrderEvent.CLOSE,       OrderStatus.CLOSED
        ));

        // 已支付：可发货、申请退款或异常关闭
        t.put(OrderStatus.PAID, Map.of(
                OrderEvent.SHIP,          OrderStatus.SHIPPED,
                OrderEvent.REFUND,  OrderStatus.REFUNDING,
                OrderEvent.CLOSE,         OrderStatus.CLOSED
        ));

        // 已发货：可签收或申请退款
        t.put(OrderStatus.SHIPPED, Map.of(
                OrderEvent.SIGN,          OrderStatus.DELIVERED,
                OrderEvent.REFUND,  OrderStatus.REFUNDING
        ));

        // 已签收：可确认完成或申请退款
        t.put(OrderStatus.DELIVERED, Map.of(
                OrderEvent.COMPLETE,       OrderStatus.COMPLETED,
                OrderEvent.REFUND,  OrderStatus.REFUNDING
        ));

        // 退款中：可退款成功，或退款失败后回到退款前状态（此处简化为已支付）
        t.put(OrderStatus.REFUNDING, Map.of(
                OrderEvent.REFUND_SUCCESS, OrderStatus.REFUNDED,
                OrderEvent.REFUND_FAILED,  OrderStatus.PAID   // 简化：驳回回原状态，实际需记录原状态
        ));

        // 终态无出边
        TABLE = Collections.unmodifiableMap(t);
    }

    /**
     * 查询一次状态迁移的目标状态。
     *
     * @param from 当前状态
     * @param event 待处理的事件
     * @return 目标状态；源状态为终态或事件不适用时返回 empty
     */
    public static Optional<OrderStatus> next(OrderStatus from, OrderEvent event) {
        return Optional.ofNullable(TABLE.getOrDefault(from, Map.of()).get(event));
    }
}