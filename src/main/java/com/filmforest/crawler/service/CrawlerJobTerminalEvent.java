package com.filmforest.crawler.service;

import com.filmforest.crawler.entity.CrawlerStatus;

/**
 * 爬虫 Job 完成事务提交后派发的稳定业务事件。
 */
public record CrawlerJobTerminalEvent(Long jobId,
                                      Long scheduleId,
                                      String scheduleName,
                                      String sourceCode,
                                      String contentType,
                                      CrawlerStatus status,
                                      Long retryOfJobId,
                                      int discovered,
                                      int added,
                                      int updated,
                                      int failed) {
}
