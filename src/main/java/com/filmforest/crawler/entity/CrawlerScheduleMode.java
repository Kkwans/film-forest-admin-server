package com.filmforest.crawler.entity;

import java.util.Locale;

public enum CrawlerScheduleMode {
    MANUAL,
    INTERVAL,
    DAILY,
    WEEKLY,
    MONTHLY,
    CUSTOM_CRON;

    public static CrawlerScheduleMode from(String value) {
        if (value == null || value.isBlank()) return MANUAL;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("不支持的定时模式: " + value, invalid);
        }
    }
}
