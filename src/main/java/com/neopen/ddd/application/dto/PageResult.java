package com.neopen.ddd.application.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 通用分页结果。
 * <p>对 Spring Data {@link Page} 的精简封装，避免把框架类型直接暴露给接口层。</p>
 *
 * @param content       当前页数据，按查询指定的排序输出
 * @param page          当前页码，从 0 开始
 * @param size          每页条数
 * @param totalElements 满足条件的总记录数
 * @param totalPages    总页数，由总记录数与每页条数计算
 * @param <T>           元素类型
 */
public record PageResult<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    /**
     * 由 Spring Data 分页对象转换。
     *
     * @param page 框架分页结果，非空
     * @param <T>  元素类型
     * @return 通用分页结果
     */
    public static <T> PageResult<T> from(Page<T> page) {
        return new PageResult<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
