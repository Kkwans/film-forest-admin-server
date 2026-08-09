package com.filmforest.crawler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crawler_job_item_failure")
public class CrawlerJobItemFailure {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long jobId;
    private String sourceCode;
    private String contentType;
    private String externalId;
    private String sourceUrl;
    private String failureStage;
    private String errorCategory;
    private Integer attemptCount;
    private Boolean retryExhausted;
    private String diagnostic;
    private LocalDateTime failedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
