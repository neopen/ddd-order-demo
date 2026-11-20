package com.neopen.ddd.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Jackson 序列化配置。
 *
 * <p>分层职责：基础设施层配置类，统一 JSON 编解码行为，供接口层与消息序列化复用，
 * 保证各出入站报文的日期格式一致。</p>
 *
 * <p>关键协作对象：Spring MVC 的消息转换器与 Kafka 的 JsonSerializer 均会使用此处
 * 注册的 {@link ObjectMapper}。</p>
 */
@Configuration
public class JacksonConfig {
    
    /**
     * 注册全局 ObjectMapper，并标记 {@code @Primary} 以覆盖 Spring Boot 默认实例。
     *
     * <p>启用 JavaTimeModule 支持 java.time 类型；关闭时间戳输出，
     * 使日期以 ISO-8601 字符串呈现，便于跨服务阅读与排查。</p>
     *
     * @return 配置好的 ObjectMapper
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        // 日期时间统一输出为 ISO-8601 字符串而非时间戳
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
