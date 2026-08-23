package com.filmforest.content.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 按当前列表筛选条件跨分页更新内容状态。 */
public record ContentStatusBatchAllRequest(
        String type,
        Integer currentStatus,
        String keyword,
        @NotNull(message = "目标状态不能为空")
        @Min(value = 0, message = "状态只允许 0、1 或 2")
        @Max(value = 2, message = "状态只允许 0、1 或 2")
        Integer targetStatus
) {
}
