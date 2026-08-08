package com.filmforest.crawler.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 由数据库聚合产生的爬虫运行统计。 */
public record CrawlerOperationsStats(
        int days,
        long jobs,
        long success,
        long partial,
        long failed,
        long cancelled,
        double avgDurationMs,
        long added,
        long updated,
        long failedItems,
        List<Daily> daily,
        List<SourceHealth> sourceHealth
) {
    public record Daily(
            LocalDate date,
            long jobs,
            long success,
            long partial,
            long failed,
            long cancelled,
            long added,
            long updated,
            long failedItems
    ) {
    }

    public record SourceHealth(
            String source,
            long jobs,
            long success,
            long partial,
            long failed,
            long cancelled,
            double avgDurationMs,
            LocalDateTime lastRunAt
    ) {
    }
}
