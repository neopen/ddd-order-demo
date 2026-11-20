package com.neopen.ddd.infrastructure.idempotency.inbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 收件箱仓储。
 */
@Repository
public interface InboxEventRepository extends JpaRepository<InboxEvent, Long> {

    /**
     * 判断某个消费者是否已接收过该事件。
     *
     * @param consumer 消费者名称
     * @param eventId  事件 ID
     * @return true 表示已消费或正在消费
     */
    boolean existsByConsumerAndEventId(String consumer, String eventId);

    /**
     * 查询消费记录，用于标记处理完成。
     *
     * @param consumer 消费者名称
     * @param eventId  事件 ID
     * @return 匹配的记录
     */
    Optional<InboxEvent> findByConsumerAndEventId(String consumer, String eventId);
}
