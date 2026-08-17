package com.filmforest.crawler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crawler_schedule_cursor")
public class CrawlerScheduleCursor {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long scheduleId;
    private String profileHash;
    private String sourceCode;
    private String contentType;
    private String sourceSort;
    private String traversalMode;
    private String querySnapshot;
    private Integer nextPage;
    private Integer nextItemIndex;
    private String nextExternalId;
    private String lastCommittedExternalId;
    private String headWatermark;
    private String state;
    private Integer cycle;
    private Long version;
    private String lastError;
    private LocalDateTime lastRunAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
