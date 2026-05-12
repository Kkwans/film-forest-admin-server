package com.filmforest.resource.entity;

import com.baomidou.mybatisplus.annotation.*;
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

    private String contentType;      // movie/drama/variety/anime/short
    private Long contentId;
    private String title;            // 资源标题（如"HD高清"）
    private String magnetUrl;        // 磁力链接
    private String resolution;       // 分辨率（1080p/4K等）
    private Boolean hasSubtitle;     // 是否有字幕
    private Boolean isSpecialSub;    // 是否特效字幕
    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    @TableField("is_deleted")
    private Integer deleted;
}
