package com.filmforest.content.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("content_poster_match")
public class ContentPosterMatch {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String contentType;
    private Long contentId;
    private String sourcePosterUrl;
    private String tmdbMediaType;
    private Long tmdbId;
    private BigDecimal tmdbScore;
    private Integer tmdbVoteCount;
    private String posterPath;
    private String posterLanguage;
    private BigDecimal confidence;
    private String matchStatus;
    private String diagnostic;
    private LocalDateTime matchedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
