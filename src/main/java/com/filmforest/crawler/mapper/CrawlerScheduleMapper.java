package com.filmforest.crawler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.filmforest.crawler.entity.CrawlerSchedule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
/**
 * 爬虫调度配置数据访问层
 * 提供 crawler_schedule 表的 CRUD 操作
 */
public interface CrawlerScheduleMapper extends BaseMapper<CrawlerSchedule> {

    @Select("SELECT * FROM crawler_schedule WHERE id = #{id} FOR UPDATE")
    CrawlerSchedule selectByIdForUpdate(@Param("id") Long id);

    @Update("""
            UPDATE crawler_schedule
            SET last_run_time = #{startedAt}
            WHERE id = #{scheduleId}
            """)
    int recordJobStarted(@Param("scheduleId") Long scheduleId,
                         @Param("startedAt") LocalDateTime startedAt);

    @Update("""
            UPDATE crawler_schedule
            SET total_runs = total_runs + 1,
                total_items = total_items + #{discovered}
            WHERE id = #{scheduleId}
            """)
    int recordJobFinished(@Param("scheduleId") Long scheduleId,
                          @Param("discovered") int discovered);
}
