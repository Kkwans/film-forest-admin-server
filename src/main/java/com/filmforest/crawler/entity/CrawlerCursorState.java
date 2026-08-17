package com.filmforest.crawler.entity;

public enum CrawlerCursorState {
    ACTIVE("ACTIVE"),
    COMPLETE("COMPLETE"),
    INVALIDATED("INVALIDATED"),
    RECOVERY_REQUIRED("RECOVERY_REQUIRED"),
    SOURCE_UNAVAILABLE("SOURCE_UNAVAILABLE");

    private final String code;

    CrawlerCursorState(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
