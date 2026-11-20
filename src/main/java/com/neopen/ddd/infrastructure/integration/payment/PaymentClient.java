package com.neopen.ddd.infrastructure.integration.payment;

import com.neopen.ddd.infrastructure.integration.payment.dto.PaymentRequestDTO;
import com.neopen.ddd.infrastructure.integration.payment.dto.PaymentResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 支付服务的 Feign 客户端声明。
 *
 * <p>分层职责：基础设施层的外部集成边界，只定义与支付服务之间的 HTTP 契约，
 * 支付规则与订单状态流转由领域层、应用层承担。</p>
 *
 * <p>关键协作对象：{@link PaymentClientFallbackFactory} 在支付服务不可用时提供
 * 降级 (Fallback)，熔断 (Circuit Breaker) 打开后请求直接进入降级逻辑。</p>
 *
 * <p>配置来源：注解内默认地址为 {@code http://localhost:8081}，实际由 application.yml 的
 * {@code payment.service.url} 覆盖（当前为 {@code http://localhost:9002}）；
 * 连接超时 2 秒、读取超时 3 秒位于
 * {@code spring.cloud.openfeign.client.config.payment-client}；熔断参数来自
 * {@code resilience4j.circuitbreaker.configs.default}。</p>
 *
 * <p>异常转换约定：远程异常由降级工厂吸收为 success=false 的响应，
 * 不向调用方抛出 Feign 异常。</p>
 */
@FeignClient(
        name = "payment-client",
        url = "${payment.service.url:http://localhost:8081}",
        fallbackFactory = PaymentClientFallbackFactory.class)
public interface PaymentClient {

    /**
     * 发起支付，对应支付服务 POST /api/payments/process。
     *
     * @param request 支付请求，含订单号、客户、金额与支付方式
     * @return 支付服务处理结果；降级时 success 为 false
     */
    @PostMapping("/api/payments/process")
    PaymentResponse processPayment(@RequestBody PaymentRequestDTO request);

    /**
     * 撤销支付，对应支付服务 POST /api/payments/cancel。
     *
     * @param request 撤销请求，复用支付请求中的订单与金额信息
     * @return 支付服务处理结果；降级时 success 为 false
     */
    @PostMapping("/api/payments/cancel")
    PaymentResponse cancelPayment(@RequestBody PaymentRequestDTO request);
}