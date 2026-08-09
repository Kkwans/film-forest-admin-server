package com.filmforest.crawler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.filmforest.crawler.entity.CrawlerJobItemFailure;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
}
