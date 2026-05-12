package com.filmforest.resource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 网盘链接资源表
 */
@Data
@TableName("resource_cloud")
public class ResourceCloud {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String contentType;      // movie/drama/variety/anime/short
    private Long contentId;
    private String diskType;         // baidu/quark/thunder/uc/123
    private String title;            // 资源标题
    private String url;              // 分享链接
    private String password;         // 提取密码
    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    @TableField("is_deleted")
    private Integer deleted;
}
