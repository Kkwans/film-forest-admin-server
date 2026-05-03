package com.filmforest.crawler.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 爬虫定时任务配置
 */
@Data
@TableName("crawler_schedule")
public class CrawlerSchedule implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务名称 */
    private String name;

    /** 内容类型: movie/drama/variety/anime/short */
    private String contentType;

    /** 来源站点 */
    private String sourceSite;

    /** 是否启用: 0-禁用 1-启用 */
    private Integer enabled;

    /** Cron 表达式 */
    private String cronExpression;

    /** 每次抓取数量 */
    private Integer batchSize;

    /** 抓取间隔(ms) */
    private Integer rateLimitMs;

    /** 优先级: by_score/by_hot/by_update */
    private String priority;

    /** 类型过滤(逗号分隔) */
    private String genreFilter;

    /** 运行状态: idle/running/stopped */
    private String status;

    /** 最后运行时间 */
    private LocalDateTime lastRunTime;

    /** 下次运行时间 */
    private LocalDateTime nextRunTime;

    /** 总运行次数 */
    private Integer totalRuns;

    /** 总抓取条目 */
    private Integer totalItems;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
