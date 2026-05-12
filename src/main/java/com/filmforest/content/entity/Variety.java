package com.filmforest.content.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("variety")
public class Variety {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;
    private String alias;
    private String posterUrl;
    private Integer year;
    private String director;
    private String writer;                  // 编剧（JSON数组）
    private String actor;
    private String genre;
    private String region;
    private String language;
    private String releaseDate;
    private Integer duration;               // 单期时长（分钟）
    private Integer totalEpisode;
    private String storyline;
    private BigDecimal scoreDouban;
    private BigDecimal scoreImdb;           // IMDB评分
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    @TableField("is_deleted")
    private Integer deleted;
}
