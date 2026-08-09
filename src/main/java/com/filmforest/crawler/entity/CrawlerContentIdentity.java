package com.filmforest.crawler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crawler_content_identity")
public class CrawlerContentIdentity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String contentType;
    private String canonicalKey;
    private String normalizedTitle;
    private Integer releaseYear;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
