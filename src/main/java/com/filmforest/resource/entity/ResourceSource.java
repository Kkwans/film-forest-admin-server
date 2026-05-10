package com.filmforest.resource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 资源来源配置表
 */
@Data
@TableName("resource_source")
public class ResourceSource {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;        // 来源名称
    private String url;         // 来源链接
    private Integer enabled;    // 是否启用
    private Integer sort;       // 排序

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
