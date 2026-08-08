package com.filmforest.crawler.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("crawler_schedule")
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

    @NotBlank(message = "来源站点不能为空")
    private String sourceSite;
    private Integer enabled;
    private String cronExpression;
    private Integer batchSize;
    private Integer rateLimitMs;
    private String priority;
    private String genreFilter;
    /**
     * 兼容既有管理端响应的瞬时展示字段，不再持久化执行状态。
     */
    @TableField(exist = false)
    private String status;
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
