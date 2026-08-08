package com.filmforest.crawler.service;

/**
 * 单次 Job 的聚合结果。Phase 2 会用类型化 Parser 进一步提高各计数精度。
 */
public record CrawlExecutionSummary(
        int discovered,
        int fetchSucceeded,
        int parseSucceeded,
        int added,
        int updated,
        int unchanged,
        int filtered,
        int failed) {

    public static CrawlExecutionSummary fromLegacyStats(int[] stats) {
        if (stats == null || stats.length < 3) {
            return new CrawlExecutionSummary(0, 0, 0, 0, 0, 0, 0, 0);
        }
        int discovered = stats[2];
        int fetchSucceeded = stats.length > 3 ? stats[3] : discovered;
        int parseSucceeded = stats.length > 4 ? stats[4] : stats[0] + stats[1];
        int filtered = stats.length > 5 ? stats[5] : 0;
        int failed = stats.length > 6 ? stats[6] : Math.max(0, discovered - parseSucceeded - filtered);
        int unchanged = stats.length > 7 ? stats[7] : 0;
        return new CrawlExecutionSummary(
                discovered, fetchSucceeded, parseSucceeded,
                stats[0], stats[1], unchanged, filtered, failed);
    }
}
