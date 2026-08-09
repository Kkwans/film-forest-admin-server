package com.filmforest.crawler.dto;

import java.util.Map;

public record CrawlerSchedulePreviewRequest(
        String scheduleMode,
        Map<String, Object> scheduleConfig,
        String cronExpression,
        String timezone
) {
}
