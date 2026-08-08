package com.filmforest.crawler.entity;

import java.util.Locale;

public enum CrawlerCrawlMode {
    LATEST("latest"),
    FULL("full");

    private final String code;

    CrawlerCrawlMode(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static CrawlerCrawlMode fromCode(String value) {
        if (value == null || value.isBlank() || "incremental".equalsIgnoreCase(value.trim())) {
            return LATEST;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (CrawlerCrawlMode mode : values()) {
            if (mode.code.equals(normalized)) return mode;
        }
        throw new IllegalArgumentException("Unsupported crawler crawlMode: " + value);
    }
}
