package com.filmforest.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("tag_source_alias")
public class TagSourceAlias {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tagId;
    private String sourceCode;
    private String contentType;
    private String alias;
}
