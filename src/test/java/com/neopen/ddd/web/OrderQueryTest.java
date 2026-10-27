package com.neopen.ddd.web;

import com.neopen.ddd.domain.model.Order;
import com.neopen.ddd.domain.repository.OrderRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@Transactional
class OrderQueryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Test
    @DisplayName("查询订单列表")
    void shouldQueryOrderList() {
        // When
        List<Order> orders = orderRepository.findAll();

        // Then
        assertNotNull(orders);
        orders.forEach(order -> {
            System.out.println("订单ID：" + order.getId());
            System.out.println("订单编号：" + order.getOrderNum());
            System.out.println("订单状态：" + order.getStatus());
            System.out.println("订单金额：" + order.getTotalAmount());
            System.out.println("------------------------");
        });
    }


    @Test
    @DisplayName("根据订单ID查询详情")
    void shouldQueryOrderDetail() {
        // Given
        Long orderId = 1L;

        // When
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() ->
                        new EntityNotFoundException("订单不存在，ID：" + orderId));

        // Then
        assertNotNull(order);
        assertEquals(orderId, order.getId());

        System.out.println("订单ID：" + order.getId());
        System.out.println("订单编号：" + order.getOrderNum());
        System.out.println("客户ID：" + order.getCustomerId());
        System.out.println("订单状态：" + order.getStatus());
        System.out.println("订单金额：" + order.getTotalAmount());
    }
}