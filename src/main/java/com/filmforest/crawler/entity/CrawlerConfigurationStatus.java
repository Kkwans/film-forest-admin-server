package com.filmforest.crawler.entity;

public enum CrawlerConfigurationStatus {
    VALIDATED("VALIDATED"),
    NEEDS_REVIEW("NEEDS_REVIEW");

    private final String code;

    CrawlerConfigurationStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
