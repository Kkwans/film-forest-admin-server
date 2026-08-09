package com.filmforest.crawler.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

public record CrawlerSchedulePreview(
        String cronExpression,
        String scheduleMode,
        Map<String, Object> scheduleConfig,
        String timezone,
        String description,
        List<ZonedDateTime> nextRuns
) {
}
