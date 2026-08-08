package com.filmforest.crawler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crawler_source_item")
public class CrawlerSourceItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String sourceCode;
    private String contentType;
    private String externalId;
    private String sourceUrl;
    private Long internalContentId;
    private String listFingerprint;
    private String detailFingerprint;
    private LocalDateTime firstSeenAt;
    private LocalDateTime lastSeenAt;
    private LocalDateTime lastFetchedAt;
    private String lastParseStatus;
    private String lastErrorCategory;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
