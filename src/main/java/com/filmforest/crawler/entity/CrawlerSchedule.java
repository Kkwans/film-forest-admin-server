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

    @NotBlank(message = "来源站点不能为空")
    private String sourceSite;
    private Integer enabled;
    private String cronExpression;
    private Integer batchSize;
    private Integer rateLimitMs;
    private String priority;
    private String genreFilter;
    private String status;
    private LocalDateTime lastRunTime;
    private LocalDateTime nextRunTime;
    private Integer totalRuns;
    private Integer totalItems;
    private Integer lastCrawledPage;  // 断点续爬：上次爬到第几页
    private Long lastCrawledId;       // 断点续爬：上次爬到哪个内容ID

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}