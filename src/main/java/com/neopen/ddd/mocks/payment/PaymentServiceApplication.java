package com.neopen.ddd.mocks.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;

/**
 * 支付模拟服务（独立进程，端口 9002）。
 * <p>启动命令：
 * {@code mvn spring-boot:run -Dspring-boot.run.main-class=com.neopen.ddd.mocks.payment.PaymentServiceApplication}</p>
 *
 * <p>定位：教学演示中的第三方支付渠道替身 (Mock Service)，供订单服务通过 Feign 调用。
 * 不连接数据库、JPA 与 Kafka，支付回执仅保存在进程内存，重启后全部丢失。</p>
 * <p>启动时把配置名切换为 {@code mock-payment}，读取独立的端口与日志配置。</p>
 */
@SpringBootApplication(
        scanBasePackages = "com.neopen.ddd.mocks.payment",
        exclude = {
                DataSourceAutoConfiguration.class,
                HibernateJpaAutoConfiguration.class,
                KafkaAutoConfiguration.class
        })
public class PaymentServiceApplication {

    /**
     * 进程入口：先指定配置文件名，再启动 Spring Boot 应用。
     * <p>系统属性必须在 {@code SpringApplication.run} 之前设置，否则配置加载阶段
     * 仍会使用默认配置文件，导致端口等参数不生效。</p>
     *
     * @param args 命令行参数，原样传递给 Spring Boot
     */
    public static void main(String[] args) {
        System.setProperty("spring.config.name", "mock-payment");
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
