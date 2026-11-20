package com.neopen.ddd.application.dto;

import com.neopen.ddd.domain.model.OrderStatus;
import lombok.Data;

/**
 * 订单列表查询条件。
 * <p>由 Spring MVC 从 URL 查询参数绑定（非 JSON 请求体），字段为空表示不参与过滤。
 * 页码与条数的边界修正由查询服务负责，本对象不做校验。</p>
 */
@Data
public class OrderQuery {

    /** 页码，从 0 开始；默认 0，负值在服务层会被修正为 0。 */
    private int page = 0;

    /** 每页条数；默认 10，服务层限制在 1 到 100 之间。 */
    private int size = 10;

    /** 订单状态精确匹配条件，为空表示不限状态。 */
    private OrderStatus status;

    /** 客户标识精确匹配条件，为空表示不限客户。 */
    private Long customerId;

    /**
     * 订单号模糊匹配。
     * <p>匹配时忽略大小写；关键字中的 SQL 通配符不做转义。</p>
     */
    private String orderNum;
}
