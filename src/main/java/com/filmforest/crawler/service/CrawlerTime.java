package com.filmforest.crawler.service;

import org.springframework.scheduling.support.CronExpression;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/** Phase 1 新时间戳统一以 UTC 的无时区数据库值保存。 */
public final class CrawlerTime {

    private static final ZoneId SCHEDULE_ZONE = ZoneId.of("Asia/Shanghai");

    private CrawlerTime() {
    }

    public static LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    public static LocalDate todayInScheduleZone() {
        return LocalDate.now(SCHEDULE_ZONE);
    }

    public static LocalDateTime startOfScheduleDayUtc(LocalDate localDate) {
        return LocalDateTime.ofInstant(localDate.atStartOfDay(SCHEDULE_ZONE).toInstant(), ZoneOffset.UTC);
    }

    public static LocalDate toScheduleDate(LocalDateTime utcDateTime) {
        return utcDateTime.atZone(ZoneOffset.UTC)
                .withZoneSameInstant(SCHEDULE_ZONE)
                .toLocalDate();
    }

    public static LocalDateTime nextRunUtc(String cronExpression, LocalDateTime referenceUtc) {
        if (cronExpression == null || cronExpression.isBlank()) {
            return null;
        }
        String normalized = cronExpression.trim();
        if (normalized.split("\\s+").length == 5) {
            normalized = "0 " + normalized;
        }
        ZonedDateTime scheduleReference = referenceUtc.atZone(ZoneOffset.UTC)
                .withZoneSameInstant(SCHEDULE_ZONE);
        ZonedDateTime next = CronExpression.parse(normalized).next(scheduleReference);
        return next == null ? null
                : LocalDateTime.ofInstant(next.toInstant(), ZoneOffset.UTC);
    }
}
