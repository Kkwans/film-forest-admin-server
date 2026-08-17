package com.filmforest.crawler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.filmforest.crawler.entity.CrawlerScheduleCursor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CrawlerScheduleCursorMapper extends BaseMapper<CrawlerScheduleCursor> {

    @Select("SELECT * FROM crawler_schedule_cursor WHERE schedule_id = #{scheduleId} FOR UPDATE")
    CrawlerScheduleCursor selectByScheduleIdForUpdate(@Param("scheduleId") Long scheduleId);

    @Select("SELECT * FROM crawler_schedule_cursor WHERE schedule_id = #{scheduleId}")
    CrawlerScheduleCursor selectByScheduleId(@Param("scheduleId") Long scheduleId);
}
