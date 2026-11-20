package com.neopen.ddd.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Kafka 事件发送器：把发件箱中的事件报文投递到订单事件主题。
 *
 * <p>默认关闭（{@code app.messaging.kafka-enabled=false}），便于在没有 Kafka 的环境中演示：
 * 关闭时 {@link #publish} 只打印日志并直接返回、不抛异常，中继会照常把事件标记为已投递，
 * 即降级为“事件仅落库、不进入消息中间件”。</p>
 *
 * <p>开启后由发件箱中继
 * {@link com.neopen.ddd.infrastructure.messaging.outbox.OutboxRelay} 调用，同步等待发送结果；
 * 失败会抛出异常，由中继累加重试次数，从而借助发件箱实现最终一致。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaEventPublisher {

    /** Kafka 模板；key 取聚合根 ID，保证同一聚合的事件落在同一分区、保持有序。 */
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /** JSON 解析器，把发件箱中的字符串报文还原为对象后再发送。 */
    private final ObjectMapper objectMapper;

    /** 是否启用 Kafka；为 {@code false} 时发送动作整体跳过。 */
    @Value("${app.messaging.kafka-enabled:false}")
    private boolean kafkaEnabled;

    /** 订单事件主题名，默认 {@code order-events}。 */
    @Value("${app.messaging.order-events-topic:order-events}")
    private String topic;

    /**
     * 发送单条事件到 Kafka。
     *
     * <p>未启用 Kafka 时直接返回（降级为仅记录日志）；启用时同步等待发送结果，
     * 最多阻塞 3 秒，超时或失败均转为异常，交由调用方重试。</p>
     *
     * @param key         消息 key，通常为聚合根 ID，用于保证分区内顺序
     * @param payloadJson 发件箱中保存的事件 JSON 报文
     * @throws IllegalStateException 发送超时、发送失败或线程被中断时抛出
     */
    public void publish(String key, String payloadJson) {
        // 降级分支：Kafka 未启用时不做任何发送，避免演示环境缺失中间件导致失败
        if (!kafkaEnabled) {
            log.info("[Kafka 未启用] 跳过事件发送: key={}, payload={}", key, payloadJson);
            return;
        }

        try {
            Object payload = objectMapper.readValue(payloadJson, Object.class);
            // 同步等待发送结果，使失败可被上层感知并计入发件箱重试
            kafkaTemplate.send(topic, key, payload).get(3, TimeUnit.SECONDS);
            log.info("Kafka 消息发送成功: topic={}, key={}", topic, key);
        } catch (InterruptedException ex) {
            // 保留中断标记，避免吞掉中断信号影响线程池状态
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka 消息发送被中断", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Kafka 消息发送失败: " + ex.getMessage(), ex);
        }
    }
}
