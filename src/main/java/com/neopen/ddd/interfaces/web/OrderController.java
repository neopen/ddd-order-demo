package com.neopen.ddd.interfaces.web;

import cn.hutool.core.util.RandomUtil;
import jakarta.validation.Valid;
import com.neopen.ddd.application.dto.OrderCreateCommand;
import com.neopen.ddd.application.service.OrderApplicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderApplicationService orderService;

    public OrderController(OrderApplicationService orderService) {
        this.orderService = orderService;
    }

    /**
     * 创建订单
     */
    @PostMapping
    public Long createOrder(@Valid @RequestBody OrderCreateCommand command) {
        return orderService.createOrder(command);
    }

    @GetMapping
    public Long createOrder() {
        OrderCreateCommand command = new OrderCreateCommand();
        command.setCustomerId(RandomUtil.randomLong(1, 100));
        command.setProductId(RandomUtil.randomLong(1, 100));
        command.setProductName("Product " + RandomUtil.randomString(5));
        command.setQuantity(RandomUtil.randomInt(1, 10));
        command.setPrice(RandomUtil.randomBigDecimal(BigDecimal.valueOf(0.01), BigDecimal.valueOf(100)));

        return orderService.createOrder(command);
    }

    /**
     * 模拟支付
     */
    @GetMapping("/{id}/pay")
    public String payOrder(@PathVariable("id") Long id) {
        return orderService.payOrder(id);
    }

    /**
     * 模拟发货
     */
    @GetMapping("/{id}/ship")
    public String shipOrder(@PathVariable("id") Long id) {
        return orderService.shipOrder(id);
    }

    /**
     * 模拟收货
     */
    @GetMapping("/{id}/receive")
    public String receiveOrder(@PathVariable("id") Long id) {
        return orderService.receiveOrder(id);
    }

    /**
     * 模拟取消
     */
    @GetMapping("/{id}/cancel")
    public String cancelOrder(@PathVariable("id") Long id) {
        return orderService.cancelOrder(id);
    }

    @GetMapping("/{id}/complete")
    public String completeOrder(@PathVariable("id") Long id) {
        return orderService.completeOrder(id);
    }

    @GetMapping("/{id}/refund")
    public String refundOrder(@PathVariable("id") Long id) {
        return orderService.refundOrder(id);
    }

    @GetMapping("/{id}/refund/succeeded")
    public String refundSucceeded(@PathVariable("id") Long id) {
        return orderService.refundSucceeded(id);
    }

    @GetMapping("/{id}/refund/failed")
    public String refundFailed(@PathVariable("id") Long id) {
        return orderService.refundFailed(id);
    }

    @GetMapping("/{id}/close")
    public String closeOrder(@PathVariable("id") Long id) {
        return orderService.closeOrder(id);
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<String> getOrderStatus(@PathVariable("id") Long id) {
        // 这里可以添加获取订单状态的逻辑
        return ResponseEntity.ok(orderService.getOrderStatus(id));
    }
}