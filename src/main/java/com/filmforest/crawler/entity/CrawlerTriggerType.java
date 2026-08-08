package com.filmforest.crawler.entity;

/** Job 的触发来源，与增量/全量 crawlMode 分离。 */
public enum CrawlerTriggerType {
    MANUAL("manual"),
    SCHEDULED("scheduled"),
    RETRY("retry");

    private final String code;

    CrawlerTriggerType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
