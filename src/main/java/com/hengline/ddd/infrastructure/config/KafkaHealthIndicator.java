package com.hengline.ddd.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka健康检查
 */
@Component
@Slf4j
public class KafkaHealthIndicator implements HealthIndicator {
    
    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;
    
    @Override
    public Health health() {
        try {
            // 尝试获取Kafka集群信息
            kafkaTemplate.send("health-check", "ping");
            return Health.up().withDetail("kafka", "Available").build();
        } catch (Exception e) {
            log.error("Kafka连接异常", e);
            return Health.down().withDetail("kafka", "Unavailable").withException(e).build();
        }
    }
}
