package com.neopen.ddd.infrastructure.idempotency;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 接口幂等 (Idempotency) 记录：保存幂等键对应的首次执行结果，重复请求直接返回该结果。
 *
 * <p>对应表 {@code tab_idempotency_record}，唯一约束 {@code uk_idempotency_scope_key}
 * 作用于 {@code (scope, idem_key)}，保证同一业务范围内同一幂等键只存在一条记录；
 * 并发插入冲突由数据库约束兜底。</p>
 *
 * <p>记录只增不改，作为请求结果的快照；过期清理需由外部策略完成，
 * 例如按 {@code created_at} 定期归档删除。</p>
 */
@Entity
@Table(
        name = "tab_idempotency_record",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_idempotency_scope_key",
                        columnNames = {"scope", "idem_key"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyRecord {

    /** 数据库自增主键，仅用于存储层标识，无业务含义。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 幂等作用域，用于隔离不同接口，例如 createOrder。 */
    @Column(name = "scope", nullable = false, length = 64)
    private String scope;

    /** 幂等键，通常由客户端传入，同一作用域下唯一。 */
    @Column(name = "idem_key", nullable = false, length = 128)
    private String idemKey;

    /** 首次执行结果的 JSON 快照，重复请求直接回放该内容。 */
    @Lob
    @Column(name = "result_json", nullable = false)
    private String resultJson;

    /** 记录创建时间，可作为过期清理与问题排查的依据。 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * 创建一条幂等记录。
     *
     * @param scope      幂等作用域，区分不同接口
     * @param idemKey    幂等键，与作用域共同构成唯一约束
     * @param resultJson 首次执行结果的 JSON 快照
     */
    public IdempotencyRecord(String scope, String idemKey, String resultJson) {
        this.scope = scope;
        this.idemKey = idemKey;
        this.resultJson = resultJson;
        this.createdAt = LocalDateTime.now();
    }
}
