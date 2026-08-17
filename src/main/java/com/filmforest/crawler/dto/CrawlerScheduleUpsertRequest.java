package com.filmforest.crawler.dto;

import com.filmforest.crawler.entity.CrawlerSchedule;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * 爬虫计划的可写配置字段。
 *
 * <p>执行状态、累计数量、检查点和运行时间均由 Job 生命周期维护，禁止通过配置表单回写。</p>
 */
public record CrawlerScheduleUpsertRequest(
        @Positive(message = "配置 ID 必须为正整数") Long id,
        @NotBlank(message = "配置名称不能为空")
        @Size(max = 100, message = "配置名称最长 100 字符") String name,
        @NotBlank(message = "内容类型不能为空") String contentType,
        String crawlMode,
        @Size(max = 100, message = "来源站点最长 100 字符") String sourceSite,
        @NotNull(message = "资源来源不能为空")
        @Positive(message = "资源来源 ID 必须为正整数") Long sourceId,
        @NotBlank(message = "来源适配器不能为空")
        @Size(max = 64, message = "来源适配器最长 64 字符") String adapterCode,
        @Min(value = 0, message = "启用状态只能为 0 或 1")
        @Max(value = 1, message = "启用状态只能为 0 或 1") Integer enabled,
        String cronExpression,
        String scheduleMode,
        Map<String, Object> scheduleConfig,
        String timezone,
        @Min(value = 1, message = "单次处理数量不能小于 1")
        @Max(value = 500, message = "单次处理数量不能超过 500") Integer batchSize,
        @Min(value = 500, message = "请求间隔不能小于 500 毫秒")
        @Max(value = 60000, message = "请求间隔不能超过 60000 毫秒") Integer rateLimitMs,
        String sourceSort,
        Map<String, String> sourceFilters,
        String traversalMode,
        String endPolicy,
        @Min(value = 1, message = "新内容上限不能小于 1") Integer newItemLimit,
        @Min(value = 1, message = "历史回填上限不能小于 1") Integer backfillItemLimit,
        @Min(value = 1, message = "人工全量上限不能小于 1") Integer manualRunLimit,
        String priority,
        String genreFilter,
        @Size(max = 100, message = "题材选择不能超过 100 项") List<@Positive Long> genreTagIds
) {

    public CrawlerSchedule toEntity() {
        CrawlerSchedule schedule = new CrawlerSchedule();
        schedule.setId(id);
        schedule.setName(name);
        schedule.setContentType(contentType);
        schedule.setCrawlMode(crawlMode);
        schedule.setSourceSite(sourceSite);
        schedule.setSourceId(sourceId);
        schedule.setAdapterCode(adapterCode);
        schedule.setEnabled(enabled);
        schedule.setCronExpression(cronExpression);
        schedule.setScheduleMode(scheduleMode);
        schedule.setScheduleConfig(scheduleConfig);
        schedule.setTimezone(timezone);
        schedule.setBatchSize(batchSize);
        schedule.setRateLimitMs(rateLimitMs);
        schedule.setSourceSort(sourceSort);
        schedule.setSourceFilters(sourceFilters);
        schedule.setTraversalMode(traversalMode);
        schedule.setEndPolicy(endPolicy);
        schedule.setNewItemLimit(newItemLimit);
        schedule.setBackfillItemLimit(backfillItemLimit);
        schedule.setManualRunLimit(manualRunLimit);
        schedule.setPriority(priority);
        schedule.setGenreFilter(genreFilter);
        schedule.setGenreTagIds(genreTagIds);
        return schedule;
    }
}
