package com.neopen.ddd.domain.statemachine;

import com.neopen.ddd.domain.model.OrderEvent;
import lombok.extern.slf4j.Slf4j;
import com.neopen.ddd.domain.model.Order;
import com.neopen.ddd.domain.model.OrderStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.StateMachineContext;
import org.springframework.statemachine.StateMachinePersist;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.statemachine.persist.DefaultStateMachinePersister;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.statemachine.guard.Guard;

import java.util.EnumSet;

/**
 * 订单状态机 (State Machine) 配置。
 *
 * <p>分层职责：位于领域层，集中声明订单全部合法迁移规则，是订单状态流转规则的唯一来源；
 * 具体执行由状态机管理器 (State Machine Manager) 驱动，本类不持有订单数据。</p>
 *
 * <p>关键协作：{@link OrderStatus} 定义状态集合，{@link OrderEvent} 定义触发事件；
 * 退款失败分支通过 Guard 读取消息头中的订单聚合根 {@link Order} 的 preStatus 选择目标状态。</p>
 *
 * <p>事务/并发：本类只构建状态机元数据；每次事件由管理器获取状态机实例并串行执行，
 * 订单状态的持久化与事务边界由应用服务负责。</p>
 */
@Configuration
@EnableStateMachineFactory(name = "orderStateMachineFactory")
@Slf4j
public class OrderStateMachineConfig extends EnumStateMachineConfigurerAdapter<OrderStatus, OrderEvent> {

    /**
     * 配置状态集合：声明初始状态与终态。
     *
     * <p>初始状态为待支付 (CREATED)；已完成、已取消、已退款、异常关闭均为终态，
     * 进入终态后不再接受任何业务事件。</p>
     *
     * @param states 状态机状态配置构建器
     * @throws Exception 状态配置非法时抛出
     */
    @Override
    public void configure(StateMachineStateConfigurer<OrderStatus, OrderEvent> states) throws Exception {
        states
                .withStates()
                // 初始状态
                .initial(OrderStatus.CREATED)
                // 终态
                .end(OrderStatus.COMPLETED)
                .end(OrderStatus.CANCELLED)
                .end(OrderStatus.REFUNDED)
                .end(OrderStatus.CLOSED)
                // 注册全部状态
                .states(EnumSet.allOf(OrderStatus.class));
    }

    /**
     * 配置状态迁移规则。
     *
     * <p>所有规则均为外部迁移 (External Transition)：触发后离开源状态并进入目标状态，
     * 每段注释按“当前状态 -> 事件 -> 目标状态”说明其业务含义。</p>
     *
     * @param transitions 状态机迁移配置构建器
     * @throws Exception 迁移配置非法时抛出
     */
    @Override
    public void configure(StateMachineTransitionConfigurer<OrderStatus, OrderEvent> transitions) throws Exception {
        transitions
                // 1. 支付订单：待支付 -> 已支付
                .withExternal()
                .source(OrderStatus.CREATED)
                .target(OrderStatus.PAID)
                .event(OrderEvent.PAY)

                // 2. 取消订单：待支付 -> 已取消
                .and()
                .withExternal()
                .source(OrderStatus.CREATED)
                .target(OrderStatus.CANCELLED)
                .event(OrderEvent.CANCEL)

                // 3. 取消订单：已支付 -> 已取消
                // 实际业务中通常需要先判断是否允许取消，以及是否需要退款
                .and()
                .withExternal()
                .source(OrderStatus.PAID)
                .target(OrderStatus.CANCELLED)
                .event(OrderEvent.CANCEL)

                // 4. 发货：已支付 -> 已发货
                .and()
                .withExternal()
                .source(OrderStatus.PAID)
                .target(OrderStatus.SHIPPED)
                .event(OrderEvent.SHIP)

                // 5. 签收：已发货 -> 已签收
                .and()
                .withExternal()
                .source(OrderStatus.SHIPPED)
                .target(OrderStatus.DELIVERED)
                .event(OrderEvent.SIGN)

                // 6. 确认完成：已签收 -> 已完成
                .and()
                .withExternal()
                .source(OrderStatus.DELIVERED)
                .target(OrderStatus.COMPLETED)
                .event(OrderEvent.COMPLETE)

                // 7. 申请退款：已支付 -> 退款中
                .and()
                .withExternal()
                .source(OrderStatus.PAID)
                .target(OrderStatus.REFUNDING)
                .event(OrderEvent.REFUND)

                // 8. 申请退款：已发货 -> 退款中
                // 需要结合退货、物流等业务规则判断是否允许
                .and()
                .withExternal()
                .source(OrderStatus.SHIPPED)
                .target(OrderStatus.REFUNDING)
                .event(OrderEvent.REFUND)

                // 9. 申请退款：已签收 -> 退款中
                // 签收后申请退款通常需先完成退货或售后确认，此处只校验状态合法性
                .and()
                .withExternal()
                .source(OrderStatus.DELIVERED)
                .target(OrderStatus.REFUNDING)
                .event(OrderEvent.REFUND)

                // 10. 退款成功：退款中 -> 已退款
                // 资金已退回，订单进入终态，不再允许发货、签收等后续操作
                .and()
                .withExternal()
                .source(OrderStatus.REFUNDING)
                .target(OrderStatus.REFUNDED)
                .event(OrderEvent.REFUND_SUCCESS)

                // 11. 退款失败：根据订单记录的 preStatus 恢复退款前状态。
                // 同一个事件配置多个带 Guard 的目标，避免在应用服务中再维护一份状态规则。
                .and()
                .withExternal()
                .source(OrderStatus.REFUNDING)
                .target(OrderStatus.PAID)
                .event(OrderEvent.REFUND_FAILED)
                // 退款中 -> 退款失败 -> 已支付：退款前状态为已支付时恢复原状态
                .guard(refundPreStatusGuard(OrderStatus.PAID))

                .and()
                .withExternal()
                .source(OrderStatus.REFUNDING)
                .target(OrderStatus.SHIPPED)
                .event(OrderEvent.REFUND_FAILED)
                // 退款中 -> 退款失败 -> 已发货：退款前状态为已发货时恢复原状态
                .guard(refundPreStatusGuard(OrderStatus.SHIPPED))

                .and()
                .withExternal()
                .source(OrderStatus.REFUNDING)
                .target(OrderStatus.DELIVERED)
                .event(OrderEvent.REFUND_FAILED)
                // 退款中 -> 退款失败 -> 已签收：退款前状态为已签收时恢复原状态
                .guard(refundPreStatusGuard(OrderStatus.DELIVERED))

                // 12. 异常关闭：待支付 -> 异常关闭
                // 例如超时未支付或风控拦截，订单不再可用，需走重新下单流程
                .and()
                .withExternal()
                .source(OrderStatus.CREATED)
                .target(OrderStatus.CLOSED)
                .event(OrderEvent.CLOSE)

                // 13. 异常关闭：已支付 -> 异常关闭
                // 需确认资金处理完成，不能直接丢弃退款义务
                .and()
                .withExternal()
                .source(OrderStatus.PAID)
                .target(OrderStatus.CLOSED)
                .event(OrderEvent.CLOSE);
    }

    /**
     * 退款失败时，只有退款前状态与该迁移目标一致，分支才允许执行。
     * Order 通过事件消息头传入，避免将动态业务状态保存在共享状态机配置中。
     *
     * @param expectedStatus 允许匹配的退款前状态
     * @return 比较订单 preStatus 与期望状态是否相等的 Guard
     */
    private Guard<OrderStatus, OrderEvent> refundPreStatusGuard(OrderStatus expectedStatus) {
        return context -> {
            // 消息头由管理器在发送事件时写入；类型不匹配视为不满足条件
            Object value = context.getMessageHeader("order");
            return value instanceof Order order && order.getPreStatus() == expectedStatus;
        };
    }

    /**
     * 状态机持久化配置。
     * 当前示例仅记录日志，并从订单对象读取状态，
     * 并未真正写入 Redis 或数据库。
     *
     * <p>读取时以订单聚合根中的状态为准，保证状态机恢复后的起点与数据库一致；
     * 写入仅用于教学演示，生产环境应接入真正的状态存储。</p>
     *
     * @return 以订单聚合根为上下文的默认状态机持久化器
     */
    @Bean("orderStateMachinePersister")
    public DefaultStateMachinePersister<OrderStatus, OrderEvent, Order> orderStateMachinePersister() {

        return new DefaultStateMachinePersister<>(
                new StateMachinePersist<>() {

                    /**
                     * 记录状态写入动作；示例实现只输出调试日志，未真正持久化。
                     *
                     * @param context 待持久化的状态机上下文
                     * @param order 订单聚合根
                     * @throws Exception 持久化失败时抛出
                     */
                    @Override
                    public void write(
                            StateMachineContext<OrderStatus, OrderEvent> context, Order order) throws Exception {

                        log.debug("write order status:{}", context.getState());
                    }

                    /**
                     * 从订单聚合根读取当前状态，构造状态机上下文。
                     *
                     * @param order 订单聚合根
                     * @return 以订单当前状态为起点的状态机上下文
                     * @throws Exception 读取失败时抛出
                     */
                    @Override
                    public StateMachineContext<OrderStatus, OrderEvent> read(Order order) throws Exception {

                        return new DefaultStateMachineContext<>(
                                order.getStatus(),
                                null,
                                null,
                                null
                        );
                    }
                }
        );
    }
}