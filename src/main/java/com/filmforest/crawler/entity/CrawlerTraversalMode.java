package com.filmforest.crawler.entity;

public enum CrawlerTraversalMode {
    CONTINUOUS_SYNC("CONTINUOUS_SYNC"),
    BACKFILL_CONTINUE("BACKFILL_CONTINUE"),
    MANUAL_FULL("MANUAL_FULL");

    private final String code;

    CrawlerTraversalMode(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
