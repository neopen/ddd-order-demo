package com.neopen.ddd.infrastructure.messaging;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import com.neopen.ddd.domain.event.DomainEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
public class KafkaEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(DomainEvent event) {
        try {
            // 确保event对象可序列化
            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send("order-events", event.getEventId(), event);

            future.whenCompleteAsync((result, ex) -> {
                if (ex != null) {
                    log.error("发送Kafka消息失败: {}", ex.getMessage(), ex);
                } else {
                    log.info("消息Kafka发送成功: {}", JSON.toJSONString(result.getProducerRecord().value()));
                }
            });

        } catch (Exception e) {
            log.error("发送Kafka消息异常", e);
        }

        log.info("--------------------- Published event: {}",JSON.toJSONString(event));
    }
}