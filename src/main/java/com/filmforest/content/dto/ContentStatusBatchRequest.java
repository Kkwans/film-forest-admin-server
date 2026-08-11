package com.filmforest.content.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 一次原子更新最多 100 条内容的三态状态。 */
public record ContentStatusBatchRequest(
        @NotEmpty(message = "至少选择一条内容")
        @Size(max = 100, message = "一次最多更新 100 条内容")
        List<@Valid ContentStatusTarget> items,
        @NotNull(message = "状态不能为空")
        @Min(value = 0, message = "状态只允许 0、1 或 2")
        @Max(value = 2, message = "状态只允许 0、1 或 2")
        Integer status
) {
}
