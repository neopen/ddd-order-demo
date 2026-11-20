package com.neopen.ddd.interfaces.web;

import cn.hutool.core.util.RandomUtil;
import com.neopen.ddd.application.dto.*;
import com.neopen.ddd.application.service.OrderApplicationService;
import com.neopen.ddd.application.service.OrderQueryService;
import com.neopen.ddd.application.saga.SagaQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 订单接口层：只负责协议适配、参数校验和调用应用服务。
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderApplicationService orderApplicationService;
    private final OrderQueryService orderQueryService;
    private final SagaQueryService sagaQueryService;

    /**
     * 创建订单。支持 Idempotency-Key 请求头实现接口幂等。
     */
    @PostMapping
    public ResponseEntity<OrderCreatedResponse> createOrder(
            @Valid @RequestBody OrderCreateCommand command,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderApplicationService.createOrder(command, idempotencyKey));
    }

    /**
     * 生成一个随机订单，便于快速演示。
     */
    @GetMapping("/demo")
    public ResponseEntity<OrderCreatedResponse> createDemoOrder() {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderApplicationService.createOrder(randomCommand(), null));
    }

    /**
     * 分页查询订单列表。
     */
    @GetMapping
    public PageResult<OrderSummaryView> list(@ModelAttribute OrderQuery query) {
        return orderQueryService.list(query);
    }

    /**
     * 查询订单详情。
     */
    @GetMapping("/{id}")
    public OrderDetailView detail(@PathVariable("id") Long id) {
        return orderQueryService.detail(id);
    }

    /**
     * 查询订单状态流转历史。
     */
    @GetMapping("/{id}/history")
    public List<OrderStatusHistoryView> history(@PathVariable("id") Long id) {
        return orderQueryService.history(id);
    }

    /**
     * 查询订单创建 Saga 的执行轨迹（步骤、尝试次数与补偿结果）。
     */
    @GetMapping("/{id}/saga")
    public SagaDetailView saga(@PathVariable("id") Long id) {
        return sagaQueryService.findByOrderId(id);
    }

    @PostMapping("/{id}/pay")
    public OrderOperationResult payOrder(
            @PathVariable("id") Long id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return orderApplicationService.payOrder(id, idempotencyKey);
    }

    @PostMapping("/{id}/ship")
    public OrderOperationResult shipOrder(@PathVariable("id") Long id) {
        return orderApplicationService.shipOrder(id);
    }

    @PostMapping("/{id}/receive")
    public OrderOperationResult receiveOrder(@PathVariable("id") Long id) {
        return orderApplicationService.receiveOrder(id);
    }

    @PostMapping("/{id}/cancel")
    public OrderOperationResult cancelOrder(@PathVariable("id") Long id) {
        return orderApplicationService.cancelOrder(id);
    }

    @PostMapping("/{id}/complete")
    public OrderOperationResult completeOrder(@PathVariable("id") Long id) {
        return orderApplicationService.completeOrder(id);
    }

    @PostMapping("/{id}/refund")
    public OrderOperationResult refundOrder(@PathVariable("id") Long id) {
        return orderApplicationService.refundOrder(id);
    }

    @PostMapping("/{id}/refund/succeeded")
    public OrderOperationResult refundSucceeded(@PathVariable("id") Long id) {
        return orderApplicationService.refundSucceeded(id);
    }

    @PostMapping("/{id}/refund/failed")
    public OrderOperationResult refundFailed(@PathVariable("id") Long id) {
        return orderApplicationService.refundFailed(id);
    }

    @PostMapping("/{id}/close")
    public OrderOperationResult closeOrder(@PathVariable("id") Long id) {
        return orderApplicationService.closeOrder(id);
    }

    private OrderCreateCommand randomCommand() {
        OrderCreateCommand command = new OrderCreateCommand();
        command.setCustomerId(RandomUtil.randomLong(1, 100));

        List<OrderItemCommand> items = new ArrayList<>();
        int itemCount = RandomUtil.randomInt(1, 3);
        for (int i = 0; i < itemCount; i++) {
            OrderItemCommand item = new OrderItemCommand();
            item.setProductId(RandomUtil.randomLong(1, 50));
            item.setProductName("Product-" + RandomUtil.randomString(5));
            item.setQuantity(RandomUtil.randomInt(1, 5));
            item.setPrice(RandomUtil.randomBigDecimal(
                    BigDecimal.valueOf(10), BigDecimal.valueOf(300)));
            items.add(item);
        }
        command.setItems(items);
        return command;
    }
}
