package com.filmforest.crawler.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("crawler_source_adapter")
public class CrawlerSourceBinding {

    @TableId
    private Long sourceId;
    private String adapterCode;
    private String contentType;
    private Integer enabled;
}
