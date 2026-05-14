package com.filmforest.crawler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.filmforest.crawler.entity.CrawlerTaskLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * 爬虫任务日志数据访问层
 * 提供 crawler_task_log 表的 CRUD 操作
 */
public interface CrawlerTaskLogMapper extends BaseMapper<CrawlerTaskLog> {
}