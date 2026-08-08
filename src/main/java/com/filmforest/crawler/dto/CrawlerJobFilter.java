package com.filmforest.crawler.dto;

import java.time.LocalDateTime;

/** 已校验、可直接绑定到服务端 Job 查询的筛选条件。 */
public record CrawlerJobFilter(
        String status,
        Long scheduleId,
        String sourceCode,
        String contentType,
        String triggerType,
        LocalDateTime from,
        LocalDateTime to,
        String keyword
) {
}
