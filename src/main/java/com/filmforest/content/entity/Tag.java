package com.filmforest.content.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * 标签表
 */
@Data
@TableName("tag")
public class Tag {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;
    private String name;
    private String color;
    private Integer sortOrder;
    private Integer usageCount;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @TableField("is_system")
    private Integer systemFlag;

    @TableLogic
    @TableField("is_deleted")
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @JsonIgnore
    public Integer getSystemFlag() {
        return systemFlag;
    }

    @JsonIgnore
    public void setSystemFlag(Integer systemFlag) {
        this.systemFlag = systemFlag;
    }

    @JsonProperty("system")
    public Integer getSystem() {
        return systemFlag;
    }

    @JsonProperty("system")
    public void setSystem(Integer system) {
        this.systemFlag = system;
    }
}
