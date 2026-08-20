package com.filmforest.crawler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 单个 Job 成功处理的内容快照，字段使用 JSON 字符串保存多值内容。 */
@Data
@TableName("crawler_job_item_success")
public class CrawlerJobItemSuccess {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long jobId;
    private String sourceCode;
    private String contentType;
    private String externalId;
    private String sourceUrl;
    private Long contentId;
    private String resultType;
    private String title;
    private String alias;
    private String posterUrl;
    private Integer year;
    private String directors;
    private String writers;
    private String actors;
    private String genres;
    private String regions;
    private String languages;
    private String releaseDate;
    private Integer duration;
    private Integer totalEpisodes;
    private BigDecimal scoreDouban;
    private BigDecimal scoreImdb;
    private BigDecimal scoreRt;
    private LocalDateTime crawledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
