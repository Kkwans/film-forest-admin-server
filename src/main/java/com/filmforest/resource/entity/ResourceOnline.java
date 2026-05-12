package com.filmforest.resource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 在线播放资源表（含剧集信息）
 */
@Data
@TableName("resource_online")
public class ResourceOnline {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String contentType;      // movie/drama/variety/anime/short
    private Long contentId;          // 内容ID

    // 剧集信息（替代原 episode 表）
    private Integer season;          // 季，默认1
    private Integer episodeNumber;   // 集号/期号
    private String episodeTitle;     // 集标题

    private String sourceName;       // 来源名称
    private String sourceUrl;        // 播放URL
    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    @TableField("is_deleted")
    private Integer deleted;
}
