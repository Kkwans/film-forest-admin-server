package com.filmforest.content.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

@Data
@TableName("tag_content_type")
public class TagContentType implements Serializable {

    @TableId
    private Long tagId;
    private String contentType;
}
