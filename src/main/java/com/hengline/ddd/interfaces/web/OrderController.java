package com.hengline.ddd.interfaces.web;

import jakarta.validation.Valid;
import com.hengline.ddd.application.dto.OrderCreateCommand;
import com.hengline.ddd.application.service.OrderApplicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    /**
     * 模拟支付
     */
    @PostMapping("/{id}/pay")
    public String payOrder(@PathVariable("id") Long id) {
        return orderService.payOrder(id);
    }

    /**
     * 模拟发货
     */
    @PostMapping("/{id}/ship")
    public String shipOrder(@PathVariable("id") Long id) {
        return orderService.shipOrder(id);
    }

    /**
     * 模拟收货
     */
    @PostMapping("/{id}/receive")
    public String reOrder(@PathVariable("id") Long id) {
        return orderService.receiveOrder(id);
    }

    /**
     * 模拟取消
     */
    @PostMapping("/{id}/cancel")
    public String cancelOrder(@PathVariable("id") Long id) {
        return orderService.cancelOrder(id);
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<String> getOrderStatus(@PathVariable("id") Long id) {
        // 这里可以添加获取订单状态的逻辑
        return ResponseEntity.ok(orderService.getOrderStatus(id));
    }
}