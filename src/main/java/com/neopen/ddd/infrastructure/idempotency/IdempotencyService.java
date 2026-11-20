package com.neopen.ddd.infrastructure.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * 接口幂等 (Idempotency) 支持服务。
 *
 * <p>同一 {@code scope + key} 只执行一次，后续请求返回首次执行结果：
 * 调用方先通过 {@link #find} 查询历史结果，未命中时执行业务并调用 {@link #record} 记录。</p>
 *
 * <p>并发冲突处理：唯一约束 {@code uk_idempotency_scope_key} 兜底，
 * 并发重复插入会抛出 {@link DataIntegrityViolationException}，此处捕获后忽略，
 * 先到请求的结果保持有效，后到请求按“已执行”处理。</p>
 *
 * <p>生产环境应结合唯一索引、客户端重试与过期清理策略使用；
 * 本类无可变状态，可被并发调用。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    /** 幂等记录仓库，负责结果快照的读取与写入。 */
    private final IdempotencyRecordRepository repository;

    /** JSON 序列化器，负责结果快照与对象之间的转换。 */
    private final ObjectMapper objectMapper;

    /**
     * 查询幂等键对应的历史执行结果。
     *
     * @param scope      幂等作用域，用于隔离不同接口
     * @param key        客户端传入的幂等键，为空时视为未启用幂等
     * @param resultType 结果反序列化目标类型
     * @param <T>        结果类型
     * @return 命中的历史结果；键为空或未命中时返回 {@link Optional#empty()}
     * @throws IllegalStateException 历史结果无法反序列化为目标类型时抛出
     */
    public <T> Optional<T> find(String scope, String key, Class<T> resultType) {
        // 幂等键为空表示调用方未要求幂等，直接放行
        if (!StringUtils.hasText(key)) {
            return Optional.empty();
        }
        return repository.findByScopeAndIdemKey(scope, key.trim())
                .map(record -> read(record.getResultJson(), resultType));
    }

    /**
     * 记录首次执行结果，供后续重复请求回放。
     *
     * <p>并发重复请求时由数据库唯一约束判定先到者，后到的插入异常被捕获并记录告警，
     * 不影响本次请求继续返回结果。</p>
     *
     * @param scope  幂等作用域
     * @param key    幂等键；为空或结果为空时不记录
     * @param result 首次执行结果，需可被 JSON 序列化
     * @throws IllegalStateException 结果无法序列化时抛出
     */
    public void record(String scope, String key, Object result) {
        if (!StringUtils.hasText(key) || result == null) {
            return;
        }
        try {
            repository.save(new IdempotencyRecord(scope, key.trim(), write(result)));
        } catch (DataIntegrityViolationException ex) {
            // 并发重复请求由数据库唯一约束兜底，后到请求直接返回已存在结果。
            log.warn("幂等记录已存在: scope={}, key={}", scope, key);
        }
    }

    /**
     * 把执行结果序列化为 JSON 快照。
     *
     * @param result 执行结果对象
     * @return JSON 字符串
     * @throws IllegalStateException 序列化失败时抛出
     */
    private String write(Object result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("幂等结果序列化失败", ex);
        }
    }

    /**
     * 把历史 JSON 快照反序列化为目标类型。
     *
     * @param json       已持久化的 JSON 字符串
     * @param resultType 目标类型
     * @param <T>        结果类型
     * @return 反序列化后的对象
     * @throws IllegalStateException 反序列化失败时抛出
     */
    private <T> T read(String json, Class<T> resultType) {
        try {
            return objectMapper.readValue(json, resultType);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("幂等结果反序列化失败", ex);
        }
    }
}
