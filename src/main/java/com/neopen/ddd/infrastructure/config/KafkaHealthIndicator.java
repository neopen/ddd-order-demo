package com.neopen.ddd.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Kafka 健康检查指示器 (Health Indicator)。
 *
 * <p>分层职责：向 Spring Boot Actuator 的 /actuator/health 汇报 Kafka 可用性，
 * 供运维与编排系统判断实例状态。</p>
 *
 * <p>降级语义：开关 {@code app.messaging.kafka-enabled} 为 false 时直接返回 UP，
 * 表示"未启用 Kafka、不参与发送"，避免产生无效连接并拖慢健康检查。</p>
 *
 * <p>关键协作对象：{@link KafkaTemplate} 用于发送探活消息。</p>
 */
@Component
@Slf4j
public class KafkaHealthIndicator implements HealthIndicator {

    /** 消息发送模板，用于向探活主题投递 ping 消息。 */
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /** Kafka 功能开关，来自 app.messaging.kafka-enabled，默认关闭。 */
    private final boolean kafkaEnabled;

    /**
     * 构造器注入模板与开关。
     *
     * @param kafkaTemplate 消息发送模板
     * @param kafkaEnabled Kafka 功能开关
     */
    public KafkaHealthIndicator(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.messaging.kafka-enabled:false}") boolean kafkaEnabled) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaEnabled = kafkaEnabled;
    }

    /**
     * 执行健康检查。
     *
     * <p>未启用 Kafka 时视为 UP（降级语义，不发起网络探测）；启用时向 health-check
     * 主题发送 ping，2 秒内未完成即判定 DOWN，避免阻塞健康端点。</p>
     *
     * @return 健康状态及 kafka 明细
     */
    @Override
    public Health health() {
        // 未启用 Kafka 时无需探测，直接视为健康
        if (!kafkaEnabled) {
            return Health.up().withDetail("kafka", "disabled").build();
        }
        try {
            // 发送探活消息并设置超时，防止 Kafka 不可用拖垮健康检查
            kafkaTemplate.send("health-check", "ping").get(2, TimeUnit.SECONDS);
            return Health.up().withDetail("kafka", "available").build();
        } catch (Exception ex) {
            log.error("Kafka 连接异常", ex);
            return Health.down().withDetail("kafka", "unavailable").withException(ex).build();
        }
    }
}
