package com.filmforest.crawler.entity;

public enum CrawlerFailureStage {
    FETCH("fetch"),
    PARSE("parse"),
    PERSISTENCE("persistence");

    private final String code;

    CrawlerFailureStage(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
