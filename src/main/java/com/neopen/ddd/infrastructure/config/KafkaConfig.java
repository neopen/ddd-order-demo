package com.neopen.ddd.infrastructure.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka 生产者配置。
 *
 * <p>分层职责：基础设施层消息配置，构建可靠的消息发送模板（KafkaTemplate），
 * 供 Outbox 中继等组件发布领域事件。</p>
 *
 * <p>关键协作对象：{@link KafkaHealthIndicator} 依赖此处注册的 KafkaTemplate 探活；
 * 是否真正发送消息由开关 {@code app.messaging.kafka-enabled} 决定，
 * 关闭时 Outbox 仍照常落库，中继跳过发送并记录日志。</p>
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    /** Kafka 集群地址，来自 spring.kafka.bootstrap-servers。 */
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * 构建生产者工厂：开启幂等与 acks=all 保证不丢消息，失败时重试 5 次。
     *
     * <p>当前未注册为独立 Bean（{@code @Bean} 已注释），仅由 {@link #kafkaTemplate()}
     * 内部调用，避免在 Kafka 未启用时提前建立连接。</p>
     *
     * @return 生产者工厂实例
     */
//    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.RETRIES_CONFIG, 5);
        props.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        return new DefaultKafkaProducerFactory<>(props);
    }

    /**
     * 注册 KafkaTemplate，作为消息发送与健康检查的统一入口。
     *
     * <p>启用 Observation 以接入 Micrometer 指标与链路追踪；该 Bean 始终创建，
     * 业务侧是否发送消息由 {@code app.messaging.kafka-enabled} 开关控制。
     * 健康检查中的 Kafka 探测可使用 {@code app.messaging.kafka-enabled} 关闭。</p>
     *
     * @return 支持可观测性的 KafkaTemplate
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        KafkaTemplate<String, Object> template = new KafkaTemplate<>(producerFactory());
        template.setObservationEnabled(true); // 启用监控
        return template;
    }
}
