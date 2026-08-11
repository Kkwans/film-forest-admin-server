package com.filmforest.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 单个跨类型内容状态变更目标。 */
public record ContentStatusTarget(
        @NotBlank(message = "内容类型不能为空") String type,
        @NotNull(message = "内容 ID 不能为空") @Positive(message = "内容 ID 必须为正整数") Long id
) {
}
