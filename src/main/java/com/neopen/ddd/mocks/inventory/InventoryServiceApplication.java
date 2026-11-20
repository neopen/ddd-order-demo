package com.neopen.ddd.mocks.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;

/**
 * 库存模拟服务（独立进程，端口 9001）。
 * <p>启动命令：
 * {@code mvn spring-boot:run -Dspring-boot.run.main-class=com.neopen.ddd.mocks.inventory.InventoryServiceApplication}</p>
 *
 * <p>定位：教学演示中的下游微服务替身 (Mock Service)，供订单服务通过 Feign 调用。
 * 服务不依赖数据库、JPA 与 Kafka，库存数据全部保存在进程内存，重启即恢复初始值。</p>
 * <p>启动时把配置名切换为 {@code mock-inventory}，读取独立的端口与日志配置，
 * 避免与主应用共用 {@code application.yml}。</p>
 */
@SpringBootApplication(
        scanBasePackages = "com.neopen.ddd.mocks.inventory",
        exclude = {
                DataSourceAutoConfiguration.class,
                HibernateJpaAutoConfiguration.class,
                KafkaAutoConfiguration.class
        })
public class InventoryServiceApplication {

    /**
     * 进程入口：先指定配置文件名，再启动 Spring Boot 应用。
     * <p>系统属性必须在 {@code SpringApplication.run} 之前设置，否则配置加载阶段
     * 仍会使用默认配置文件，导致端口等参数不生效。</p>
     *
     * @param args 命令行参数，原样传递给 Spring Boot
     */
    public static void main(String[] args) {
        System.setProperty("spring.config.name", "mock-inventory");
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
}
