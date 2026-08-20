package com.filmforest.resource.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 磁力链接资源表
 */
@Data
@TableName("resource_magnet")
public class ResourceMagnet {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotBlank(message = "内容类型不能为空")
    private String contentType;      // movie/drama/variety/anime/short

    @NotNull(message = "内容 ID 不能为空")
    private Long contentId;

    private String sourceCode;
    private String resourceKey;
    private String rawText;
    private LocalDateTime lastSeenAt;
    private LocalDateTime removedAt;
    private Integer enabled;

    private String title;            // 资源标题（如"HD高清"）

    @NotBlank(message = "磁力链接不能为空")
    private String magnetUrl;        // 磁力链接
    private String resolution;       // 分辨率（1080p/4K等）
    private Boolean hasSubtitle;     // 是否有字幕
    private Boolean isSpecialSub;    // 是否特效字幕
    private Integer sort;

    /** 管理端展示用关联内容摘要，不映射资源表字段。 */
    @TableField(exist = false)
    private String contentTitle;
    @TableField(exist = false)
    private String contentAlias;
    @TableField(exist = false)
    private String contentPosterUrl;
    @TableField(exist = false)
    private Integer contentYear;
    @TableField(exist = false)
    private String contentReleaseDate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    @TableField("is_deleted")
    private Integer deleted;
}
