package com.neopen.ddd.mocks.inventory;

import com.neopen.ddd.infrastructure.integration.inventory.dto.InventoryRequestDTO;
import com.neopen.ddd.infrastructure.integration.inventory.dto.InventoryResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 内存库存服务 (In-Memory Inventory Service)：模拟运行在端口 9001 的独立库存微服务，
 * 提供库存预占、释放与查询能力，供订单服务的库存防腐层 (ACL) 通过 HTTP 调用。
 * <p>数据结构：以商品 ID 为键、原子整数 (AtomicInteger) 为值的并发映射保存库存，
 * 商品首次被访问时惰性初始化为 {@link #INITIAL_STOCK} 件。</p>
 * <p>线程安全：映射使用 {@link ConcurrentHashMap}，预占通过 CAS
 * (Compare-And-Swap) 循环更新，避免并发下超卖；释放使用原子加法，不引入全局锁。</p>
 * <p>状态易失：库存只存在于进程内存，重启后恢复初始值，仅用于联调与教学演示。</p>
 */
@RestController
@RequestMapping("/api/inventory")
@Slf4j
public class InventoryMockController {

    /** 每个商品的初始库存数量，商品首次被访问时按此值创建计数器。 */
    private static final int INITIAL_STOCK = 1000;

    /** 商品库存表：键为商品 ID，值为剩余库存的原子计数器，支持并发读写。 */
    private final Map<Long, AtomicInteger> stocks = new ConcurrentHashMap<>();

    /**
     * 预占库存：校验请求后以 CAS 循环扣减可用库存。
     * <p>业务规则：库存不足或参数非法时返回失败结果而不抛异常，由调用方决定是否补偿；
     * CAS 失败说明期间有其他请求并发修改库存，重新读取后重试，因此不会超卖。</p>
     *
     * @param request 预占请求，包含商品 ID 与数量
     * @return 预占结果，成功时携带剩余库存，失败时携带失败原因
     */
    @PostMapping("/reserve")
    public InventoryResponse reserve(@RequestBody InventoryRequestDTO request) {
        // 校验：非法请求直接以失败结果返回，不进入库存计算
        if (request == null || request.getProductId() == null || request.getQuantity() <= 0) {
            return InventoryResponse.fail("商品ID或数量非法");
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
                log.info("库存预占成功: productId={}, quantity={}, remaining={}",
                        request.getProductId(), request.getQuantity(),
                        available - request.getQuantity());
                return InventoryResponse.of(
                        true, "RESERVED", "库存预占成功", available - request.getQuantity());
            }
        }
    }

    /**
     * 释放库存：把指定数量原子地加回到商品库存。
     * <p>用于订单取消或创建失败后的反向补偿。接口不做上限校验，重复释放会使库存虚增，
     * 因此调用方应依赖订单的预占标记保证只释放一次（演示场景的简化处理）。</p>
     *
     * @param request 释放请求，包含商品 ID 与数量
     * @return 释放结果，成功时携带释放后的库存
     */
    @PostMapping("/release")
    public InventoryResponse release(@RequestBody InventoryRequestDTO request) {
        if (request == null || request.getProductId() == null || request.getQuantity() <= 0) {
            return InventoryResponse.fail("商品ID或数量非法");
        }

        int remaining = stockOf(request.getProductId())
                .addAndGet(request.getQuantity());
        log.info("库存释放成功: productId={}, quantity={}, remaining={}",
                request.getProductId(), request.getQuantity(), remaining);
        return InventoryResponse.of(true, "RELEASED", "库存释放成功", remaining);
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
