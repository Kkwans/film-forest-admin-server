package com.filmforest.crawler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.filmforest.crawler.entity.CrawlerJobItemFailure;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CrawlerJobItemFailureMapper extends BaseMapper<CrawlerJobItemFailure> {

    @Insert("""
            INSERT INTO crawler_job_item_failure (
              job_id, source_code, content_type, external_id, source_url,
              failure_stage, error_category, attempt_count, retry_exhausted,
              diagnostic, failed_at
            ) VALUES (
              #{failure.jobId}, #{failure.sourceCode}, #{failure.contentType},
              #{failure.externalId}, #{failure.sourceUrl}, #{failure.failureStage},
              #{failure.errorCategory}, #{failure.attemptCount}, #{failure.retryExhausted},
              #{failure.diagnostic}, #{failure.failedAt}
            )
            ON DUPLICATE KEY UPDATE
              source_url = VALUES(source_url),
              failure_stage = VALUES(failure_stage),
              error_category = VALUES(error_category),
              attempt_count = GREATEST(attempt_count, VALUES(attempt_count)),
              retry_exhausted = VALUES(retry_exhausted),
              diagnostic = VALUES(diagnostic),
              failed_at = VALUES(failed_at)
            """)
    int upsertFailure(@Param("failure") CrawlerJobItemFailure failure);

    @Select("""
            <script>
            SELECT * FROM crawler_job_item_failure
            WHERE job_id = #{jobId}
              <if test="stage != null">AND failure_stage = #{stage}</if>
              <if test="category != null">AND error_category = #{category}</if>
              <if test="retryExhausted != null">AND retry_exhausted = #{retryExhausted}</if>
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<CrawlerJobItemFailure> selectFailurePage(@Param("jobId") Long jobId,
                                                  @Param("stage") String stage,
                                                  @Param("category") String category,
                                                  @Param("retryExhausted") Boolean retryExhausted,
                                                  @Param("limit") int limit,
                                                  @Param("offset") long offset);

    @Select("""
            <script>
            SELECT COUNT(*) FROM crawler_job_item_failure
            WHERE job_id = #{jobId}
              <if test="stage != null">AND failure_stage = #{stage}</if>
              <if test="category != null">AND error_category = #{category}</if>
              <if test="retryExhausted != null">AND retry_exhausted = #{retryExhausted}</if>
            </script>
            """)
    long countFailures(@Param("jobId") Long jobId,
                       @Param("stage") String stage,
                       @Param("category") String category,
                       @Param("retryExhausted") Boolean retryExhausted);
}
