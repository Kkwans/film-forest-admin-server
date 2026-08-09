package com.filmforest.crawler.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("crawler_schedule_genre")
public class CrawlerScheduleGenre {

    @TableId
    private Long scheduleId;
    private Long tagId;
}
