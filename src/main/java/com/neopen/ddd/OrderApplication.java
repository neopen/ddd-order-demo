package com.neopen.ddd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * <b>
 * <p>
 *
 * @Author neopen
 * @Github https://github.com/neopen/ddd-order-demo
 * @Date 2025/8/27 15:06
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "com.neopen.ddd.infrastructure.integration")
public class OrderApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}