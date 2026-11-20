package com.neopen.ddd.infrastructure.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 订单事件消费者示例：演示其他微服务订阅订单事件。
 *
 * <p>默认不启动（{@code app.messaging.consumer-enabled=false}），开启 Kafka 后可通过配置启用。
 * 消费者组由 {@code app.messaging.consumer-group} 指定，支持多实例按分区负载均衡。</p>
 *
 * <p>示例仅打印日志；真实业务需按事件 ID 做幂等 (Idempotency) 处理，
 * 因为 Kafka 采用至少一次投递语义，可能产生重复消费。</p>
 */
@Component
@Slf4j
public class OrderEventKafkaConsumer {

    /**
     * 消费订单事件。
     *
     * <p>方法体保持轻量，避免长时间阻塞拉取线程；此处不捕获异常，
     * 交由 Spring Kafka 容器的重试与错误处理策略处置。</p>
     *
     * @param event 反序列化后的事件报文，键为事件字段名
     */
    @KafkaListener(
            topics = "${app.messaging.order-events-topic:order-events}",
            groupId = "${app.messaging.consumer-group:order-ddd-consumer}",
            autoStartup = "${app.messaging.consumer-enabled:false}")
    public void consume(Map<String, Object> event) {
        log.info("收到订单事件: {}", event);
    }
}
