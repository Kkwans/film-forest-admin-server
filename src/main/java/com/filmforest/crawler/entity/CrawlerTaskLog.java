package com.filmforest.crawler.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
/**
 * 爬虫任务日志实体
 * 记录每次爬取任务的执行状态、抓取数量和耗时
 */
public class CrawlerTaskLog {

    private Long id;
    private Long scheduleId;
    private String scheduleName;
    private String contentType;
    private String status;
    private Integer itemsCrawled;
    private Integer itemsAdded;
    private Integer itemsUpdated;
    private String errorMessage;
    private Integer durationMs;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}