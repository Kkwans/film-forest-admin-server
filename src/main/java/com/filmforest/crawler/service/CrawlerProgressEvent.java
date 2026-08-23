package com.filmforest.crawler.service;

import com.filmforest.crawler.entity.CrawlerTaskLog;

import java.util.List;

/** 管理端爬虫实时事件；仅包含已落库的 Job 快照，不暴露凭据或请求内容。 */
public record CrawlerProgressEvent(
        String type,
        Long jobId,
        CrawlerTaskLog job,
        List<CrawlerTaskLog> jobs
) {

    public static CrawlerProgressEvent job(String type, CrawlerTaskLog job) {
        return new CrawlerProgressEvent(type, job == null ? null : job.getId(), job, null);
    }

    public static CrawlerProgressEvent snapshot(List<CrawlerTaskLog> jobs) {
        return new CrawlerProgressEvent("snapshot", null, null, jobs == null ? List.of() : jobs);
    }
}
