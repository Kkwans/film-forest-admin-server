package com.filmforest.content.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("variety")
/**
 * 综艺实体类
 * 对应数据库 variety 表，存储综艺节目基本信息、评分和状态
 */
public class Variety {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotBlank(message = "综艺标题不能为空")
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
