package com.filmforest.crawler.dto;

import com.filmforest.crawler.entity.CrawlerTaskLog;

import java.time.LocalDateTime;

/**
 * 手工启动或重试后立即返回的权威 Job 摘要。
 *
 * <p>字段来自同一条已入库的 {@link CrawlerTaskLog}，避免调用方再按
 * schedule 查询“最新 Job”而产生竞态。</p>
 */
public record CrawlerJobStartResult(
        Long jobId,
        String status,
        LocalDateTime queuedAt
) {

    public static CrawlerJobStartResult from(CrawlerTaskLog job) {
        if (job == null) {
            return null;
        }
        return new CrawlerJobStartResult(job.getId(), job.getStatus(), job.getQueuedAt());
    }
}
