package com.filmforest.crawler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.filmforest.crawler.entity.CrawlerTaskLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
/**
 * 爬虫任务日志数据访问层
 * 提供 crawler_task_log 表的 CRUD 操作
 */
public interface CrawlerTaskLogMapper extends BaseMapper<CrawlerTaskLog> {

    @Select("""
            SELECT * FROM crawler_task_log
            WHERE schedule_id = #{scheduleId}
              AND status IN ('queued', 'running', 'cancel_requested')
            ORDER BY id DESC
            LIMIT 1
            """)
    CrawlerTaskLog selectActiveByScheduleId(@Param("scheduleId") Long scheduleId);

    @Select("""
            SELECT * FROM crawler_task_log
            WHERE status IN ('queued', 'running', 'cancel_requested')
            ORDER BY queued_at ASC, id ASC
            """)
    List<CrawlerTaskLog> selectActiveJobs();

    @Update("""
            UPDATE crawler_task_log
            SET status = 'running', started_at = #{now}, heartbeat_at = #{now},
                progress_updated_at = #{now}
            WHERE id = #{jobId} AND status = 'queued'
            """)
    int claimQueuedJob(@Param("jobId") Long jobId, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE crawler_task_log
            SET cancel_requested = 1,
                finished_at = CASE WHEN status = 'queued' THEN #{now} ELSE finished_at END,
                duration_ms = CASE WHEN status = 'queued' THEN 0 ELSE duration_ms END,
                status = CASE WHEN status = 'queued' THEN 'cancelled' ELSE 'cancel_requested' END,
                progress_updated_at = #{now}
            WHERE id = #{jobId} AND status IN ('queued', 'running', 'cancel_requested')
            """)
    int requestCancel(@Param("jobId") Long jobId, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE crawler_task_log
            SET current_page = #{currentPage}, current_item = #{currentItem},
                discovered_count = #{discovered},
                fetch_succeeded_count = #{fetchSucceeded},
                parse_succeeded_count = #{parseSucceeded},
                added_count = #{added}, updated_count = #{updated},
                unchanged_count = #{unchanged}, filtered_count = #{filtered},
                failed_count = #{failed}, checkpoint = #{checkpoint},
                items_crawled = #{discovered}, items_added = #{added}, items_updated = #{updated},
                heartbeat_at = #{now}, progress_updated_at = #{now}
            WHERE id = #{jobId} AND status IN ('running', 'cancel_requested')
            """)
    int updateProgress(@Param("jobId") Long jobId,
                       @Param("currentPage") Integer currentPage,
                       @Param("currentItem") String currentItem,
                       @Param("discovered") int discovered,
                       @Param("fetchSucceeded") int fetchSucceeded,
                       @Param("parseSucceeded") int parseSucceeded,
                       @Param("added") int added,
                       @Param("updated") int updated,
                       @Param("unchanged") int unchanged,
                       @Param("filtered") int filtered,
                       @Param("failed") int failed,
                       @Param("checkpoint") String checkpoint,
                       @Param("now") LocalDateTime now);

    @Update("""
            UPDATE crawler_task_log
            SET heartbeat_at = #{now}, progress_updated_at = #{now}
            WHERE id = #{jobId} AND status IN ('running', 'cancel_requested')
            """)
    int touchHeartbeat(@Param("jobId") Long jobId, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE crawler_task_log
            SET status = 'interrupted', finished_at = #{now},
                duration_ms = CASE
                  WHEN started_at IS NULL THEN 0
                  ELSE TIMESTAMPDIFF(MICROSECOND, started_at, #{now}) DIV 1000
                END,
                error_summary = COALESCE(error_summary, 'Job heartbeat expired'),
                error_message = COALESCE(error_message, 'Job heartbeat expired')
            WHERE status IN ('running', 'cancel_requested')
              AND (heartbeat_at IS NULL OR heartbeat_at < #{staleBefore})
            """)
    int interruptStaleJobs(@Param("staleBefore") LocalDateTime staleBefore,
                           @Param("now") LocalDateTime now);

    @Select("""
            SELECT * FROM crawler_task_log
            WHERE status = 'queued'
            ORDER BY queued_at ASC, id ASC
            LIMIT #{limit}
            """)
    List<CrawlerTaskLog> selectQueuedJobs(@Param("limit") int limit);
}
