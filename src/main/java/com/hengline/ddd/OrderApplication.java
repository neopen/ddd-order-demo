package com.hengline.ddd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * <b>
 * <p>
 *
 * @Author HengLine
 * @Email haeng2030@gmail.com
 * @Date 2025/8/27 15:06
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "com.hengline.ddd.infrastructure.integration")
public class OrderApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}