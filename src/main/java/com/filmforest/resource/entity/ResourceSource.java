package com.filmforest.resource.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotBlank;
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

    @NotBlank(message = "来源名称不能为空")
    private String name;        // 来源名称

    @NotBlank(message = "来源链接不能为空")
    private String url;         // 来源链接
    private Integer enabled;    // 是否启用
    private Integer sort;       // 排序

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
