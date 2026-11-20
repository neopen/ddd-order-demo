package com.neopen.ddd.interfaces.web;

import com.neopen.ddd.application.dto.OrderReadModelView;
import com.neopen.ddd.application.service.OrderReadModelQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 订单读模型查询接口：演示 CQRS 读侧独立于写模型的查询路径。
 *
 * <p>与 {@code /api/orders} 写模型查询并存，便于对比两种读法的差异：
 * 写模型查询强一致但需要加载聚合；读模型查询只读投影表，适合列表与报表场景。</p>
 */
@RestController
@RequestMapping("/api/read-models/orders")
@RequiredArgsConstructor
public class OrderReadModelController {

    private final OrderReadModelQueryService readModelQueryService;

    /**
     * 查询读模型列表。
     *
     * @param status 可选状态过滤
     * @return 最近更新的订单摘要投影
     */
    @GetMapping
    public List<OrderReadModelView> list(@RequestParam(value = "status", required = false) String status) {
        return readModelQueryService.list(status);
    }

    /**
     * 按订单号查询读模型详情。
     *
     * @param orderNum 订单号
     * @return 订单摘要投影
     */
    @GetMapping("/{orderNum}")
    public OrderReadModelView detail(@PathVariable("orderNum") String orderNum) {
        return readModelQueryService.detail(orderNum);
    }
}
