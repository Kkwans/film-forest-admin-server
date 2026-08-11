package com.filmforest.resource.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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

    @NotBlank(message = "内容类型不能为空")
    private String contentType;      // movie/drama/variety/anime/short

    @NotNull(message = "内容 ID 不能为空")
    @Positive(message = "内容 ID 必须为正整数")
    private Long contentId;          // 内容ID

    private String sourceCode;
    private String resourceKey;
    private String rawText;
    private LocalDateTime lastSeenAt;
    private LocalDateTime removedAt;
    private Integer enabled;

    // 剧集信息（替代原 episode 表）
    private Integer season;          // 季，默认1
    private Integer episodeNumber;   // 集号/期号
    private String episodeTitle;     // 集标题

    @NotBlank(message = "来源名称不能为空")
    private String sourceName;       // 来源名称
    @NotBlank(message = "播放 URL 不能为空")
    private String sourceUrl;        // 播放URL
    @Size(max = 1000, message = "来源详情页 URL 不能超过 1000 个字符")
    private String sourcePageUrl;    // 来源站播放页，用于降级和溯源
    private String playbackType;     // HLS/VIDEO/EMBED/EXTERNAL_PAGE
    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    @TableField("is_deleted")
    private Integer deleted;
}
