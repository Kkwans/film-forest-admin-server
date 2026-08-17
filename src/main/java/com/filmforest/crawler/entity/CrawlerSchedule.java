package com.filmforest.crawler.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@TableName(value = "crawler_schedule", autoResultMap = true)
/**
 * 爬虫调度配置实体
 * 对应 crawler_schedule 表，存储定时爬取任务的配置信息
 */
public class CrawlerSchedule {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotBlank(message = "配置名称不能为空")
    @Size(max = 100, message = "配置名称最长 100 字符")
    private String name;

    @NotBlank(message = "内容类型不能为空")
    private String contentType;

    private String crawlMode;

    private String sourceSite;
    @NotNull(message = "资源来源不能为空")
    private Long sourceId;
    @NotBlank(message = "来源适配器不能为空")
    private String adapterCode;
    private Integer enabled;
    private String cronExpression;
    private String scheduleMode;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> scheduleConfig;
    private String timezone;
    private Integer batchSize;
    private Integer rateLimitMs;
    /** 新契约字段；priority 仅保留兼容读取。 */
    private String sourceSort;
    @TableField(value = "source_filter_json", typeHandler = JacksonTypeHandler.class)
    private Map<String, String> sourceFilters;
    private String traversalMode;
    private String endPolicy;
    private Integer newItemLimit;
    private Integer backfillItemLimit;
    private Integer manualRunLimit;
    private String configurationStatus;
    private String configurationIssue;
    private String queryProfileHash;
    private String priority;
    private String genreFilter;
    @TableField(exist = false)
    private List<Long> genreTagIds;
    /**
     * 兼容既有管理端响应的瞬时展示字段，不再持久化执行状态。
     */
    @TableField(exist = false)
    private String status;
    /** 最近一次权威 Job，仅用于管理端配置列表展示。 */
    @TableField(exist = false)
    private Long latestJobId;
    /** 最近一次权威 Job 的终态或活动态。 */
    @TableField(exist = false)
    private String latestResult;
    private LocalDateTime lastRunTime;
    private LocalDateTime nextRunTime;
    private Integer totalRuns;
    private Integer totalItems;
    /** @deprecated 执行检查点已迁移到 crawler_task_log Job。 */
    @Deprecated
    private Integer lastCrawledPage;
    /** @deprecated 执行检查点已迁移到 crawler_task_log Job。 */
    @Deprecated
    private Long lastCrawledId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
