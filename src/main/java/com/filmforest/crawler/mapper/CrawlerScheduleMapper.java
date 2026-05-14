package com.filmforest.crawler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.filmforest.crawler.entity.CrawlerSchedule;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * 爬虫调度配置数据访问层
 * 提供 crawler_schedule 表的 CRUD 操作
 */
public interface CrawlerScheduleMapper extends BaseMapper<CrawlerSchedule> {
}