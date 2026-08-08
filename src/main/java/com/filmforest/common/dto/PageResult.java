package com.filmforest.common.dto;

import java.util.List;

/** 不暴露 ORM 实现的稳定分页响应。 */
public record PageResult<T>(
        List<T> records,
        long total,
        long size,
        long current,
        long pages
) {
}
