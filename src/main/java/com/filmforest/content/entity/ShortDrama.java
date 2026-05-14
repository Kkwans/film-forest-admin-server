package com.filmforest.content.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("short_drama")
/**
 * 短剧实体类
 * 对应数据库 short_drama 表，存储短剧基本信息、评分和状态
 */
public class ShortDrama {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;
    private String alias;
    private String posterUrl;
    private Integer year;
    private String director;
    private String actor;
    private String genre;
    private String region;
    private String language;
    private String releaseDate;
    private Integer totalEpisode;
    private Integer duration;
    private String storyline;
    private BigDecimal scoreDouban;         // 豆瓣评分
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
