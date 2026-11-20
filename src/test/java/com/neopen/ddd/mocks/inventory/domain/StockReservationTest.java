package com.neopen.ddd.mocks.inventory.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 库存预占聚合单元测试：验证 PENDING 出发的三条状态迁移与非法迁移保护。
 *
 * <p>关注点：确认、释放、过期三类业务结局互斥；重复释放保持幂等；
 * 已确认的预占不能释放，避免已售出库存被错误归还。</p>
 */
class StockReservationTest {

    /**
     * 场景：预占后由订单支付成功触发确认。
     * <p>断言：状态从 PENDING 迁移为 CONFIRMED，之后重复确认被拒绝。</p>
     */
    @Test
    @DisplayName("预占确认：PENDING -> CONFIRMED，且不能重复确认")
    void shouldConfirmPendingReservation() {
        StockReservation reservation = StockReservation.create(11L, 2, "ORD-1", 900);

        assertEquals(StockReservation.Status.PENDING, reservation.getStatus());
        reservation.confirm();

        assertEquals(StockReservation.Status.CONFIRMED, reservation.getStatus());
        assertThrows(IllegalStateException.class, reservation::confirm);
    }

    /**
     * 场景：订单取消或创建失败时释放预占，补偿逻辑可能重复执行。
     * <p>断言：首次释放返回 true（需要归还库存），再次释放返回 false，
     * 保证库存不会被重复归还。</p>
     */
    @Test
    @DisplayName("释放预占：首次归还库存，重复释放保持幂等")
    void shouldReleaseOnlyOnce() {
        StockReservation reservation = StockReservation.create(11L, 2, "ORD-2", 900);

        assertTrue(reservation.release());
        assertEquals(StockReservation.Status.RELEASED, reservation.getStatus());
        assertFalse(reservation.release());
    }

    /**
     * 场景：预占超时未被订单确认。
     * <p>断言：到期前不迁移；到期后迁移为 EXPIRED 且只归还一次库存。</p>
     */
    @Test
    @DisplayName("过期回收：到期才迁移为 EXPIRED，且只归还一次库存")
    void shouldExpireAfterTtl() {
        StockReservation reservation = StockReservation.create(11L, 3, "ORD-3", 60);
        LocalDateTime beforeExpiry = reservation.getExpiresAt().minusSeconds(1);

        assertFalse(reservation.expireIfDue(beforeExpiry));
        assertEquals(StockReservation.Status.PENDING, reservation.getStatus());

        assertTrue(reservation.expireIfDue(reservation.getExpiresAt().plusSeconds(1)));
        assertEquals(StockReservation.Status.EXPIRED, reservation.getStatus());
        assertFalse(reservation.expireIfDue(reservation.getExpiresAt().plusSeconds(2)));
    }

    /**
     * 场景：已确认（库存已真正消耗）的预占收到释放请求。
     * <p>断言：拒绝释放，防止已售库存被错误归还。</p>
     */
    @Test
    @DisplayName("已确认的预占不允许释放")
    void shouldRejectReleaseAfterConfirm() {
        StockReservation reservation = StockReservation.create(11L, 1, "ORD-4", 900);
        reservation.confirm();

        assertThrows(IllegalStateException.class, reservation::release);
    }

    /**
     * 场景：创建预占单时传入非法参数。
     * <p>断言：数量、订单号与 TTL 的非法组合都被提前拦截。</p>
     */
    @Test
    @DisplayName("非法参数不能创建预占单")
    void shouldRejectInvalidArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> StockReservation.create(null, 1, "ORD-5", 900));
        assertThrows(IllegalArgumentException.class,
                () -> StockReservation.create(11L, 0, "ORD-5", 900));
        assertThrows(IllegalArgumentException.class,
                () -> StockReservation.create(11L, 1, " ", 900));
        assertThrows(IllegalArgumentException.class,
                () -> StockReservation.create(11L, 1, "ORD-5", 0));
    }
}
