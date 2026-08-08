package com.filmforest.crawler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("crawler_task_log")
/**
 * 权威爬虫 Job；保留旧日志字段用于阶段性 API/统计兼容。
 */
public class CrawlerTaskLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long scheduleId;
    private String scheduleName;
    private String contentType;
    private String sourceCode;
    private String crawlMode;
    private String triggerType;
    private Long retryOfJobId;
    private String status;
    private Boolean cancelRequested;
    private Integer currentPage;
    private String currentItem;
    private Integer discoveredCount;
    private Integer fetchSucceededCount;
    private Integer parseSucceededCount;
    private Integer addedCount;
    private Integer updatedCount;
    private Integer unchangedCount;
    private Integer filteredCount;
    private Integer failedCount;
    private String checkpoint;
    private LocalDateTime heartbeatAt;
    private LocalDateTime progressUpdatedAt;
    private String errorSummary;
    private LocalDateTime queuedAt;

    /** 由数据库生成的活动 Job 唯一键，不参与写入。 */
    @TableField(exist = false)
    private Long activeScheduleId;

    // 旧字段：Phase 5 API 迁移完成前继续同步写入。
    private Integer itemsCrawled;
    private Integer itemsAdded;
    private Integer itemsUpdated;
    private String errorMessage;
    private Long durationMs;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
