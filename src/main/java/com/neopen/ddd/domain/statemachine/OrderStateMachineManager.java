package com.neopen.ddd.domain.statemachine;

import com.neopen.ddd.domain.model.Order;
import com.neopen.ddd.domain.model.OrderEvent;
import com.neopen.ddd.domain.model.OrderStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.persist.StateMachinePersister;
import org.springframework.stereotype.Component;

/**
 * 状态机基础设施管理器。
 *
 * <p>负责状态机实例的获取、状态恢复、事件发送和生命周期管理；
 * 不负责订单聚合持久化，也不发布领域事件。</p>
 *
 * <p>关键协作：依赖状态机工厂创建实例、状态机持久化器恢复订单状态；
 * 事件消息头中的订单对象供配置层的 Guard 判断退款前状态。</p>
 *
 * <p>并发：工厂可能按 machineId 复用同一实例，因此对实例加锁串行执行；
 * 该锁只覆盖单个 JVM，跨节点并发仍需依赖数据库乐观锁兜底。</p>
 */
@Component
@Slf4j
public class OrderStateMachineManager {

    /** 事件消息头键：携带订单聚合根，供 Guard 读取退款前状态。 */
    private static final String ORDER_HEADER = "order";
    /** 事件消息头键：携带订单 ID，供状态迁移监听器输出追踪日志。 */
    private static final String ORDER_ID_HEADER = "orderId";

    /** 状态机工厂：按订单维度创建或复用状态机实例。 */
    private final StateMachineFactory<OrderStatus, OrderEvent> stateMachineFactory;
    /** 状态机持久化器：把订单当前状态恢复为状态机起点。 */
    private final StateMachinePersister<OrderStatus, OrderEvent, Order> persister;

    /**
     * 构造状态机管理器。
     *
     * @param stateMachineFactory 状态机工厂，按名称注入订单专用工厂
     * @param persister 状态机持久化器，按名称注入订单专用实现
     */
    public OrderStateMachineManager(
            @Qualifier("orderStateMachineFactory")
            StateMachineFactory<OrderStatus, OrderEvent> stateMachineFactory,
            @Qualifier("orderStateMachinePersister")
            StateMachinePersister<OrderStatus, OrderEvent, Order> persister) {
        this.stateMachineFactory = stateMachineFactory;
        this.persister = persister;
    }

    /**
     * 驱动状态机处理一次订单事件，并返回迁移后的目标状态。
     * 订单状态的实际持久化由应用服务和 JPA 事务负责。
     *
     * <p>执行顺序：启动状态机 -> 恢复订单当前状态 -> 发送带订单消息头的事件
     * -> 校验事件被接受且产生了目标状态 -> 停止状态机。</p>
     *
     * <p>本方法只回答“该事件是否合法、目标状态是什么”，
     * 不修改内存中的订单聚合根，也不写数据库。</p>
     *
     * @param order 已持久化的订单，必须包含 ID 与当前状态
     * @param event 待处理的订单领域事件
     * @return 状态机计算出的目标状态
     * @throws IllegalArgumentException 订单、订单 ID 或事件为空时抛出
     * @throws IllegalStateException 事件被拒绝、目标状态缺失或执行异常时抛出
     */
    public OrderStatus sendEvent(Order order, OrderEvent event) {
        // 恢复状态与写入消息头都依赖订单 ID，参数缺失必须尽早失败
        if (order == null || order.getId() == null || event == null) {
            throw new IllegalArgumentException("订单、已持久化的订单ID和事件不能为空");
        }

        StateMachine<OrderStatus, OrderEvent> machine =
                stateMachineFactory.getStateMachine("order-" + order.getId());

        // 工厂可能按 machineId 返回同一个实例；同一 JVM 内对该实例串行执行。
        synchronized (machine) {
            try {
                // 1. 先启动状态机
                machine.start();

                // 2. 再恢复订单在数据库中的实际状态
                persister.restore(machine, order);

                // 3. 使用当前订单状态处理事件
                Message<OrderEvent> message = MessageBuilder
                        .withPayload(event)
                        .setHeader(ORDER_HEADER, order)
                        .setHeader(ORDER_ID_HEADER, order.getId())
                        .build();

                boolean accepted = machine.sendEvent(message);

                // 事件被拒绝说明当前状态不允许该迁移，属于业务异常而非系统故障
                if (!accepted) {
                    throw new IllegalStateException(
                            "状态机拒绝订单事件: orderId=" + order.getId()
                                    + ", currentStatus=" + order.getStatus()
                                    + ", event=" + event);
                }

                // 缺少目标状态意味着状态机配置不完整，继续执行会写入错误状态
                if (machine.getState() == null
                        || machine.getState().getId() == null) {
                    throw new IllegalStateException(
                            "状态机处理后未能获取目标状态，orderId=" + order.getId());
                }

                return machine.getState().getId();

            } catch (Exception ex) {
                // 保留业务异常原始类型，受检异常统一包装为运行时异常
                if (ex instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }

                throw new IllegalStateException(
                        "订单状态机执行失败: orderId=" + order.getId()
                                + ", event=" + event,
                        ex);
            } finally {
                // 实例可能被复用，必须停止以清理内部状态；停止失败不掩盖主异常
                try {
                    machine.stop();
                } catch (Exception stopError) {
                    log.warn("停止订单状态机失败，orderId={}",
                            order.getId(), stopError);
                }
            }
        }
    }
}
