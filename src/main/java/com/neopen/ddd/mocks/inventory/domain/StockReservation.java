package com.neopen.ddd.mocks.inventory.domain;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 库存预占单聚合根 (Stock Reservation Aggregate Root)。
 *
 * <p>定位：库存上下文中的聚合根，代表“某笔订单暂时占用了若干库存”这一业务事实。
 * 订单上下文只保存预占单号，不持有库存对象，两个上下文通过 ID 协作，
 * 避免跨服务共享领域模型。</p>
 *
 * <p>状态机：</p>
 * <pre>
 *   PENDING --confirm--> CONFIRMED   支付成功，库存被真正消耗
 *   PENDING --release--> RELEASED    订单取消/创建失败，库存归还
 *   PENDING --expire-->  EXPIRED     TTL 到期未确认，库存自动归还
 * </pre>
 *
 * <p>不变量：只有 PENDING 可以迁移；释放与过期都会归还库存，且只能归还一次，
 * 因此拒绝 CONFIRMED 状态的释放请求。实例非线程安全，由库存服务在
 * {@code ConcurrentHashMap} 上按预占单号串行化访问。</p>
 */
public class StockReservation {

    /** 预占单状态。 */
    public enum Status {
        /** 已预占，等待订单支付确认或取消释放。 */
        PENDING,
        /** 已确认，订单支付成功，库存被真正消耗。 */
        CONFIRMED,
        /** 已释放，库存已归还可售池。 */
        RELEASED,
        /** 已过期，TTL 到期自动回收。 */
        EXPIRED
    }

    /** 预占单号，全局唯一，供订单侧确认或释放时引用。 */
    private final String id;

    /** 商品标识。 */
    private final Long productId;

    /** 预占数量。 */
    private final int quantity;

    /** 归属订单号，便于库存侧按订单对账。 */
    private final String orderNum;

    /** 当前状态。 */
    private Status status;

    /** 预占到期时间，PENDING 状态下超过该时间由定时任务回收。 */
    private final LocalDateTime expiresAt;

    /** 最后一次状态变更时间。 */
    private LocalDateTime updatedAt;

    /**
     * 创建预占单，初始状态为 PENDING。
     *
     * @param productId  商品标识，不能为空
     * @param quantity   预占数量，必须大于 0
     * @param orderNum   归属订单号，不能为空白
     * @param ttlSeconds 有效期（秒），必须大于 0
     * @return 新的预占单
     * @throws IllegalArgumentException 参数非法时抛出
     */
    public static StockReservation create(Long productId, int quantity,
                                          String orderNum, int ttlSeconds) {
        if (productId == null) {
            throw new IllegalArgumentException("商品ID不能为空");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("预占数量必须大于0");
        }
        if (orderNum == null || orderNum.isBlank()) {
            throw new IllegalArgumentException("订单号不能为空");
        }
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("预占有效期必须大于0");
        }
        LocalDateTime now = LocalDateTime.now();
        return new StockReservation(
                "RSV-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16),
                productId, quantity, orderNum, now, now.plusSeconds(ttlSeconds));
    }

    private StockReservation(String id, Long productId, int quantity, String orderNum,
                             LocalDateTime createdAt, LocalDateTime expiresAt) {
        this.id = id;
        this.productId = productId;
        this.quantity = quantity;
        this.orderNum = orderNum;
        this.status = Status.PENDING;
        this.updatedAt = createdAt;
        this.expiresAt = expiresAt;
    }

    /**
     * 确认预占：PENDING -> CONFIRMED，表示库存被真正消耗。
     *
     * @throws IllegalStateException 当前不处于 PENDING 时抛出
     */
    public void confirm() {
        requirePending("确认");
        this.status = Status.CONFIRMED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 释放预占：PENDING -> RELEASED，库存归还可售池。
     * <p>对已释放或已过期的预占单保持幂等：重复释放不会再次归还库存，
     * 便于补偿任务安全重试。</p>
     *
     * @return true 表示本次调用实际释放了库存，调用方据此决定是否归还库存
     * @throws IllegalStateException 预占单已确认时抛出
     */
    public boolean release() {
        if (status == Status.RELEASED || status == Status.EXPIRED) {
            return false;
        }
        if (status == Status.CONFIRMED) {
            throw new IllegalStateException("已确认的预占不能释放: " + id);
        }
        this.status = Status.RELEASED;
        this.updatedAt = LocalDateTime.now();
        return true;
    }

    /**
     * 判断预占是否已到期，到期则迁移为 EXPIRED。
     *
     * @param now 当前时间，由定时任务统一传入，便于测试控制时钟
     * @return true 表示本次调用实际使其过期，调用方需归还库存
     */
    public boolean expireIfDue(LocalDateTime now) {
        if (status != Status.PENDING || now.isBefore(expiresAt)) {
            return false;
        }
        this.status = Status.EXPIRED;
        this.updatedAt = now;
        return true;
    }

    /**
     * 校验状态必须为 PENDING，否则拒绝本次迁移。
     *
     * @param action 操作名称，用于错误信息
     */
    private void requirePending(String action) {
        if (status != Status.PENDING) {
            throw new IllegalStateException("预占单当前状态不允许" + action + ": " + status);
        }
    }

    /** @return 预占单号 */
    public String getId() {
        return id;
    }

    /** @return 商品标识 */
    public Long getProductId() {
        return productId;
    }

    /** @return 预占数量 */
    public int getQuantity() {
        return quantity;
    }

    /** @return 归属订单号 */
    public String getOrderNum() {
        return orderNum;
    }

    /** @return 当前状态 */
    public Status getStatus() {
        return status;
    }

    /** @return 预占到期时间 */
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    /** @return 最后状态变更时间 */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof StockReservation that)) {
            return false;
        }
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
