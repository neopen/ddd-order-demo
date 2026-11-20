package com.neopen.ddd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 订单服务启动类。
 *
 * <p>只扫描订单服务自身的分层包，避免把 mocks 下的独立模拟服务装配进来；
 * 模拟服务通过各自的启动类运行在不同端口。</p>
 */
@SpringBootApplication(scanBasePackages = {
        "com.neopen.ddd.application",
        "com.neopen.ddd.domain",
        "com.neopen.ddd.infrastructure",
        "com.neopen.ddd.interfaces"
})
@EnableFeignClients(basePackages = "com.neopen.ddd.infrastructure.integration")
@EnableScheduling
public class OrderApplication {

    /**
     * 启动订单服务。
     *
     * @param args 命令行参数，原样传递给 Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
