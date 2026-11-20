package com.neopen.ddd.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neopen.ddd.contracts.IntegrationEvent;
import com.neopen.ddd.infrastructure.idempotency.inbox.InboxService;
import com.neopen.ddd.infrastructure.readmodel.OrderSummaryProjector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 订单事件消费者：演示其他微服务如何以“信封 + 收件箱 + 投影”的方式订阅订单事件。
 *
 * <p>处理流程：</p>
 * <ol>
 *   <li>从信封读取 {@code eventId} 与 {@code eventType}；</li>
 *   <li>通过 {@link InboxService} 抢占消费权，重复消息直接跳过；</li>
 *   <li>交给 {@link OrderSummaryProjector} 更新读模型；</li>
 *   <li>标记消费完成。</li>
 * </ol>
 *
 * <p>默认不启动（{@code app.messaging.consumer-enabled=false}）；
 * 开启 Kafka 与消费者后，本类负责把 {@code order-events} 主题的事件投影到本地读模型。</p>
 *
 * <p>注意：消费逻辑保持轻量，避免阻塞拉取线程；处理异常向上抛出，
 * 由 Spring Kafka 容器按重试与错误处理策略处置。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventKafkaConsumer {

    /** 消费者名称，作为收件箱去重的作用域。 */
    private static final String CONSUMER = "order-summary-projection";

    private final InboxService inboxService;
    private final OrderSummaryProjector projector;
    private final ObjectMapper objectMapper;

    /**
     * 消费订单集成事件。
     *
     * @param message 反序列化后的信封报文，字段为信封的驼峰命名
     */
    @KafkaListener(
            topics = "${app.messaging.order-events-topic:order-events}",
            groupId = "${app.messaging.consumer-group:order-ddd-consumer}",
            autoStartup = "${app.messaging.consumer-enabled:false}")
    @Transactional
    public void consume(Map<String, Object> message) {
        String eventId = text(message, "eventId");
        String eventType = text(message, "eventType");
        if (eventId == null || eventType == null) {
            // 兼容旧格式消息：没有信封元数据时无法可靠去重，只记录日志。
            log.warn("收到缺少信封元数据的消息，已忽略: {}", message);
            return;
        }
        if (!inboxService.tryConsume(CONSUMER, eventId, eventType)) {
            log.info("重复事件已跳过: eventId={}, eventType={}", eventId, eventType);
            return;
        }

        IntegrationEvent envelope = new IntegrationEvent(
                eventId,
                eventType,
                message.get("version") instanceof Number number ? number.intValue() : 1,
                text(message, "aggregateType"),
                text(message, "aggregateId"),
                text(message, "occurredAt"),
                objectMapper.valueToTree(message.get("payload")));
        projector.applyIntegrationEvent(envelope);
        inboxService.markProcessed(CONSUMER, eventId);
        log.info("订单事件已消费: eventId={}, eventType={}", eventId, eventType);
    }

    /**
     * 从报文中读取文本字段。
     *
     * @param message 信封报文
     * @param field   字段名
     * @return 字段文本，缺失时返回 null
     */
    private String text(Map<String, Object> message, String field) {
        Object value = message.get(field);
        return value == null ? null : value.toString();
    }
}
