package com.filmforest.crawler.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record CrawlerSourceQueryPreviewRequest(
        @NotBlank(message = "来源适配器不能为空") String sourceCode,
        @NotBlank(message = "内容类型不能为空") String contentType,
        String sort,
        Map<String, String> sourceFilters,
        @Min(value = 1, message = "页码必须大于 0")
        @Max(value = 10000, message = "页码不能超过 10000") Integer page
) {
}
