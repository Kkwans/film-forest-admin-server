package com.filmforest.crawler.entity;

public enum CrawlerEndPolicy {
    HOLD_COMPLETED("HOLD_COMPLETED"),
    RESTART_CYCLE("RESTART_CYCLE");

    private final String code;

    CrawlerEndPolicy(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
