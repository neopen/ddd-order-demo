package com.neopen.ddd.mocks.inventory;

import com.neopen.ddd.infrastructure.integration.inventory.dto.InventoryRequestDTO;
import com.neopen.ddd.infrastructure.integration.inventory.dto.InventoryResponse;
import com.neopen.ddd.mocks.inventory.domain.StockReservation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 内存库存服务 (In-Memory Inventory Service)：模拟运行在端口 9001 的独立库存微服务。
 *
 * <p>核心模型：库存扣减不再是一次性永久扣减，而是生成带 TTL 的预占单
 * {@link StockReservation}。预占单生命周期为
 * {@code PENDING -> CONFIRMED / RELEASED / EXPIRED}，对应“支付确认、订单取消、超时回收”
 * 三种业务结局，从而避免订单服务崩溃时库存被永久占用。</p>
 *
 * <p>数据结构：商品剩余库存用 {@link AtomicInteger} 保存，预占单保存于
 * {@link ConcurrentHashMap}；预占扣减使用 CAS 循环，避免并发超卖。</p>
 *
 * <p>定时任务：每 5 秒扫描一次到期的 PENDING 预占单，迁移为 EXPIRED 并归还库存；
 * 这正是“库存 Reservation 聚合 + TTL”在微服务中的落地方式。</p>
 *
 * <p>状态易失：库存与预占仅存在于进程内存，重启后恢复初始值，仅用于联调与教学演示。</p>
 */
@RestController
@RequestMapping("/api/inventory")
@Slf4j
public class InventoryMockController {

    /** 每个商品的初始库存数量，商品首次被访问时按此值创建计数器。 */
    private static final int INITIAL_STOCK = 1000;

    /** 预占有效期默认值（秒），请求未显式指定时使用。 */
    private static final int DEFAULT_TTL_SECONDS = 900;

    /** 商品库存表：键为商品 ID，值为剩余库存的原子计数器，支持并发读写。 */
    private final Map<Long, AtomicInteger> stocks = new ConcurrentHashMap<>();

    /** 预占单表：键为预占单号，值为预占聚合，模拟库存上下文中的聚合存储。 */
    private final Map<String, StockReservation> reservations = new ConcurrentHashMap<>();

    /**
     * 创建库存预占单：先 CAS 扣减可售库存，再生成 PENDING 预占单。
     * <p>两步都在同一个内存操作序列中完成，演示场景不做事务；库存不足或参数非法时
     * 返回失败结果而不抛异常，由订单侧决定是否补偿。</p>
     *
     * @param request 预占请求，包含商品、数量、订单号与可选 TTL
     * @return 预占结果，成功时携带预占单号与剩余库存
     */
    @PostMapping("/reservations")
    public InventoryResponse reserve(@RequestBody InventoryRequestDTO request) {
        if (request == null || request.getProductId() == null || request.getQuantity() <= 0) {
            return InventoryResponse.fail("商品ID或数量非法");
        }
        if (request.getOrderNum() == null || request.getOrderNum().isBlank()) {
            return InventoryResponse.fail("订单号不能为空");
        }

        AtomicInteger stock = stockOf(request.getProductId());
        // 执行：CAS 循环扣减，失败说明库存被并发修改，重读最新值后重试
        while (true) {
            int available = stock.get();
            if (available < request.getQuantity()) {
                log.warn("库存不足: productId={}, available={}, required={}",
                        request.getProductId(), available, request.getQuantity());
                return InventoryResponse.fail("库存不足，剩余 " + available);
            }
            if (stock.compareAndSet(available, available - request.getQuantity())) {
                int ttl = request.getTtlSeconds() == null || request.getTtlSeconds() <= 0
                        ? DEFAULT_TTL_SECONDS : request.getTtlSeconds();
                StockReservation reservation = StockReservation.create(
                        request.getProductId(), request.getQuantity(), request.getOrderNum(), ttl);
                reservations.put(reservation.getId(), reservation);
                log.info("库存预占成功: reservationId={}, productId={}, quantity={}, remaining={}",
                        reservation.getId(), request.getProductId(), request.getQuantity(),
                        available - request.getQuantity());
                return InventoryResponse.builder()
                        .success(true)
                        .status(reservation.getStatus().name())
                        .message("库存预占成功")
                        .reservationId(reservation.getId())
                        .data(available - request.getQuantity())
                        .build();
            }
        }
    }

    /**
     * 确认预占：PENDING -> CONFIRMED，表示订单已支付、库存被真正消耗。
     *
     * @param reservationId 预占单号
     * @return 确认结果，状态为 CONFIRMED
     */
    @PostMapping("/reservations/{reservationId}/confirm")
    public InventoryResponse confirm(@PathVariable("reservationId") String reservationId) {
        StockReservation reservation = reservations.get(reservationId);
        if (reservation == null) {
            return InventoryResponse.fail("预占单不存在: " + reservationId);
        }
        try {
            reservation.confirm();
            log.info("库存预占已确认: reservationId={}, orderNum={}",
                    reservationId, reservation.getOrderNum());
            return reservationResponse(reservation, "库存预占已确认");
        } catch (IllegalStateException ex) {
            return InventoryResponse.fail(ex.getMessage());
        }
    }

    /**
     * 释放预占：PENDING -> RELEASED，并把库存归还到可售池。
     * <p>重复释放是幂等的：已释放或已过期的预占单不会再次归还库存，
     * 便于订单侧的补偿逻辑安全重试。</p>
     *
     * @param reservationId 预占单号
     * @return 释放结果，状态为 RELEASED
     */
    @PostMapping("/reservations/{reservationId}/release")
    public InventoryResponse release(@PathVariable("reservationId") String reservationId) {
        StockReservation reservation = reservations.get(reservationId);
        if (reservation == null) {
            return InventoryResponse.fail("预占单不存在: " + reservationId);
        }
        try {
            if (reservation.release()) {
                stockOf(reservation.getProductId()).addAndGet(reservation.getQuantity());
                log.info("库存预占已释放: reservationId={}, quantity={}",
                        reservationId, reservation.getQuantity());
            }
            return reservationResponse(reservation, "库存预占已释放");
        } catch (IllegalStateException ex) {
            return InventoryResponse.fail(ex.getMessage());
        }
    }

    /**
     * 查询预占单详情，便于联调时观察状态机迁移结果。
     *
     * @param reservationId 预占单号
     * @return 预占单快照；不存在时返回失败结果
     */
    @GetMapping("/reservations/{reservationId}")
    public InventoryResponse queryReservation(@PathVariable("reservationId") String reservationId) {
        StockReservation reservation = reservations.get(reservationId);
        if (reservation == null) {
            return InventoryResponse.fail("预占单不存在: " + reservationId);
        }
        return reservationResponse(reservation, "查询成功");
    }

    /**
     * 查询商品当前可售库存。
     * <p>未知商品会按初始库存惰性建档，因此查询不存在的商品返回初始值而非“未找到”。</p>
     *
     * @param productId 商品 ID
     * @return 查询结果，成功时携带当前库存
     */
    @GetMapping("/{productId}")
    public InventoryResponse query(@PathVariable("productId") Long productId) {
        return InventoryResponse.of(
                true, "OK", "查询成功", stockOf(productId).get());
    }

    /**
     * 定时回收到期预占：PENDING 且超过 expiresAt 的预占单迁移为 EXPIRED 并归还库存。
     * <p>这是跨服务场景下处理“订单创建后进程崩溃、预占无人确认”的兜底手段。</p>
     */
    @Scheduled(fixedDelay = 5000)
    public void expireReservations() {
        LocalDateTime now = LocalDateTime.now();
        for (StockReservation reservation : reservations.values()) {
            if (reservation.expireIfDue(now)) {
                stockOf(reservation.getProductId()).addAndGet(reservation.getQuantity());
                log.info("库存预占已过期并回收: reservationId={}, productId={}, quantity={}",
                        reservation.getId(), reservation.getProductId(), reservation.getQuantity());
            }
        }
    }

    /**
     * 把预占聚合转换为响应 DTO。
     *
     * @param reservation 预占聚合
     * @param message     提示信息
     * @return 包含预占单号与状态的响应
     */
    private InventoryResponse reservationResponse(StockReservation reservation, String message) {
        return InventoryResponse.builder()
                .success(true)
                .status(reservation.getStatus().name())
                .message(message)
                .reservationId(reservation.getId())
                .data(reservation.getExpiresAt())
                .build();
    }

    /**
     * 获取商品库存计数器，不存在时按初始库存惰性创建。
     * <p>{@code computeIfAbsent} 在并发映射上是原子操作，保证同一商品只初始化一次。</p>
     *
     * @param productId 商品 ID，调用前已保证非空
     * @return 该商品的库存计数器
     */
    private AtomicInteger stockOf(Long productId) {
        return stocks.computeIfAbsent(productId, id -> new AtomicInteger(INITIAL_STOCK));
    }
}
