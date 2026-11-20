package com.neopen.ddd.infrastructure.messaging.outbox;

/**
 * 事务性发件箱 (Transactional Outbox) 事件状态。
 *
 * <p>状态流转：{@code PENDING -> PUBLISHED}（中继投递成功）；
 * {@code PENDING -> PENDING}（失败但未达上限，等待下一轮重试）；
 * {@code PENDING -> FAILED}（失败次数达到上限，停止自动重试，需人工干预）。</p>
 */
public enum OutboxStatus {

    /** 待投递：已与业务数据同事务落库，等待中继扫描并发送到 Kafka。 */
    PENDING,

    /** 已投递：中继成功发送到 Kafka，属终态，不再被扫描。 */
    PUBLISHED,

    /** 投递失败且重试次数达到上限：属终态，需人工排查后重置为 PENDING。 */
    FAILED
}
