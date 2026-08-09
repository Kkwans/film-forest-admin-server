package com.filmforest.crawler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.filmforest.crawler.dto.CrawlerJobFilter;
import com.filmforest.crawler.entity.CrawlerTaskLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
            WHERE schedule_id = #{scheduleId}
            ORDER BY queued_at DESC, id DESC
            LIMIT 1
            """)
    CrawlerTaskLog selectLatestByScheduleId(@Param("scheduleId") Long scheduleId);

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
            SET heartbeat_at = #{now}
            WHERE id = #{jobId} AND status IN ('running', 'cancel_requested')
            """)
    int touchHeartbeat(@Param("jobId") Long jobId, @Param("now") LocalDateTime now);

    @Select("""
            SELECT id FROM crawler_task_log
            WHERE status IN ('running', 'cancel_requested')
              AND (heartbeat_at IS NULL OR heartbeat_at < #{staleBefore})
            ORDER BY id ASC
            """)
    List<Long> selectHeartbeatExpiredJobIds(@Param("staleBefore") LocalDateTime staleBefore);

    @Update("""
            UPDATE crawler_task_log
            SET status = 'interrupted', finished_at = #{now},
                duration_ms = CASE
                  WHEN started_at IS NULL THEN 0
                  ELSE TIMESTAMPDIFF(MICROSECOND, started_at, #{now}) DIV 1000
                END,
                error_summary = COALESCE(error_summary, 'Job heartbeat expired'),
                error_message = COALESCE(error_message, 'Job heartbeat expired')
            WHERE id = #{jobId}
              AND status IN ('running', 'cancel_requested')
              AND (heartbeat_at IS NULL OR heartbeat_at < #{staleBefore})
            """)
    int interruptHeartbeatExpiredJob(@Param("jobId") Long jobId,
                                     @Param("staleBefore") LocalDateTime staleBefore,
                                     @Param("now") LocalDateTime now);

    @Select("""
            SELECT id FROM crawler_task_log
            WHERE status = 'running'
              AND cancel_requested = 0
              AND (progress_updated_at IS NULL OR progress_updated_at < #{stalledBefore})
            ORDER BY id ASC
            """)
    List<Long> selectProgressStalledJobIds(@Param("stalledBefore") LocalDateTime stalledBefore);

    @Update("""
            UPDATE crawler_task_log
            SET status = 'cancel_requested', cancel_requested = 1,
                error_summary = COALESCE(error_summary, 'Job progress stalled'),
                error_message = COALESCE(error_message, 'Job progress stalled')
            WHERE id = #{jobId}
              AND status = 'running'
              AND cancel_requested = 0
              AND (progress_updated_at IS NULL OR progress_updated_at < #{stalledBefore})
            """)
    int requestProgressStalledCancellation(@Param("jobId") Long jobId,
                                           @Param("stalledBefore") LocalDateTime stalledBefore);

    @Select("""
            SELECT * FROM crawler_task_log
            WHERE status = 'queued'
            ORDER BY queued_at ASC, id ASC
            LIMIT #{limit}
            """)
    List<CrawlerTaskLog> selectQueuedJobs(@Param("limit") int limit);

    @Select("""
            <script>
            SELECT * FROM crawler_task_log
            <where>
              <if test="filter.status != null">AND status = #{filter.status}</if>
              <if test="filter.scheduleId != null">AND schedule_id = #{filter.scheduleId}</if>
              <if test="filter.sourceCode != null">AND source_code = #{filter.sourceCode}</if>
              <if test="filter.contentType != null">AND content_type = #{filter.contentType}</if>
              <if test="filter.triggerType != null">AND trigger_type = #{filter.triggerType}</if>
              <if test="filter.from != null">AND COALESCE(started_at, queued_at) &gt;= #{filter.from}</if>
              <if test="filter.to != null">AND COALESCE(started_at, queued_at) &lt; #{filter.to}</if>
              <if test="filter.keyword != null">
                AND (
                  INSTR(LOWER(COALESCE(schedule_name, '')), LOWER(#{filter.keyword})) &gt; 0
                  OR INSTR(LOWER(COALESCE(current_item, '')), LOWER(#{filter.keyword})) &gt; 0
                  OR INSTR(LOWER(COALESCE(error_summary, '')), LOWER(#{filter.keyword})) &gt; 0
                  OR INSTR(LOWER(COALESCE(source_code, '')), LOWER(#{filter.keyword})) &gt; 0
                )
              </if>
            </where>
            ORDER BY queued_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<CrawlerTaskLog> selectJobPage(@Param("filter") CrawlerJobFilter filter,
                                       @Param("limit") int limit,
                                       @Param("offset") long offset);

    @Select("""
            <script>
            SELECT COUNT(*) FROM crawler_task_log
            <where>
              <if test="filter.status != null">AND status = #{filter.status}</if>
              <if test="filter.scheduleId != null">AND schedule_id = #{filter.scheduleId}</if>
              <if test="filter.sourceCode != null">AND source_code = #{filter.sourceCode}</if>
              <if test="filter.contentType != null">AND content_type = #{filter.contentType}</if>
              <if test="filter.triggerType != null">AND trigger_type = #{filter.triggerType}</if>
              <if test="filter.from != null">AND COALESCE(started_at, queued_at) &gt;= #{filter.from}</if>
              <if test="filter.to != null">AND COALESCE(started_at, queued_at) &lt; #{filter.to}</if>
              <if test="filter.keyword != null">
                AND (
                  INSTR(LOWER(COALESCE(schedule_name, '')), LOWER(#{filter.keyword})) &gt; 0
                  OR INSTR(LOWER(COALESCE(current_item, '')), LOWER(#{filter.keyword})) &gt; 0
                  OR INSTR(LOWER(COALESCE(error_summary, '')), LOWER(#{filter.keyword})) &gt; 0
                  OR INSTR(LOWER(COALESCE(source_code, '')), LOWER(#{filter.keyword})) &gt; 0
                )
              </if>
            </where>
            </script>
            """)
    long countJobs(@Param("filter") CrawlerJobFilter filter);

    @Select("""
            SELECT COUNT(*) AS jobs,
                   COALESCE(SUM(status = 'success'), 0) AS success,
                   COALESCE(SUM(status = 'partial_success'), 0) AS partial,
                   COALESCE(SUM(status = 'failed'), 0) AS failed,
                   COALESCE(SUM(status IN ('cancelled', 'interrupted')), 0) AS cancelled,
                   COALESCE(AVG(CASE WHEN finished_at IS NOT NULL THEN duration_ms END), 0) AS avgDurationMs,
                   COALESCE(SUM(added_count), 0) AS added,
                   COALESCE(SUM(updated_count), 0) AS updated,
                   COALESCE(SUM(failed_count), 0) AS failedItems
            FROM crawler_task_log
            WHERE COALESCE(started_at, queued_at) >= #{from}
              AND COALESCE(started_at, queued_at) < #{to}
            """)
    Map<String, Object> selectOperationsSummary(@Param("from") LocalDateTime from,
                                                 @Param("to") LocalDateTime to);

    @Select("""
            SELECT DATE(DATE_ADD(COALESCE(started_at, queued_at), INTERVAL 8 HOUR)) AS day,
                   COUNT(*) AS jobs,
                   COALESCE(SUM(status = 'success'), 0) AS success,
                   COALESCE(SUM(status = 'partial_success'), 0) AS partial,
                   COALESCE(SUM(status = 'failed'), 0) AS failed,
                   COALESCE(SUM(status IN ('cancelled', 'interrupted')), 0) AS cancelled,
                   COALESCE(SUM(added_count), 0) AS added,
                   COALESCE(SUM(updated_count), 0) AS updated,
                   COALESCE(SUM(failed_count), 0) AS failedItems
            FROM crawler_task_log
            WHERE COALESCE(started_at, queued_at) >= #{from}
              AND COALESCE(started_at, queued_at) < #{to}
            GROUP BY day
            ORDER BY day ASC
            """)
    List<Map<String, Object>> selectDailyOperations(@Param("from") LocalDateTime from,
                                                     @Param("to") LocalDateTime to);

    @Select("""
            SELECT COALESCE(NULLIF(source_code, ''), 'unknown') AS source,
                   COUNT(*) AS jobs,
                   COALESCE(SUM(status = 'success'), 0) AS success,
                   COALESCE(SUM(status = 'partial_success'), 0) AS partial,
                   COALESCE(SUM(status = 'failed'), 0) AS failed,
                   COALESCE(SUM(status IN ('cancelled', 'interrupted')), 0) AS cancelled,
                   COALESCE(AVG(CASE WHEN finished_at IS NOT NULL THEN duration_ms END), 0) AS avgDurationMs,
                   MAX(COALESCE(started_at, queued_at)) AS lastRunAt
            FROM crawler_task_log
            WHERE COALESCE(started_at, queued_at) >= #{from}
              AND COALESCE(started_at, queued_at) < #{to}
            GROUP BY COALESCE(NULLIF(source_code, ''), 'unknown')
            ORDER BY jobs DESC, source ASC
            """)
    List<Map<String, Object>> selectSourceHealth(@Param("from") LocalDateTime from,
                                                  @Param("to") LocalDateTime to);
}
