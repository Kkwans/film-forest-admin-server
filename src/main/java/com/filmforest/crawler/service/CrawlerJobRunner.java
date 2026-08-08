package com.filmforest.crawler.service;

import com.filmforest.crawler.core.CrawlerCore;
import com.filmforest.crawler.entity.CrawlerTaskLog;
import com.filmforest.crawler.mapper.CrawlerScheduleMapper;
import com.filmforest.crawler.mapper.CrawlerTaskLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class CrawlerJobRunner {

    private static final CrawlExecutionSummary EMPTY_SUMMARY =
            new CrawlExecutionSummary(0, 0, 0, 0, 0, 0, 0, 0);

    private final CrawlerTaskLogMapper jobMapper;
    private final CrawlerScheduleMapper scheduleMapper;
    private final CrawlerCore crawlerCore;
    private final CrawlerJobCoordinator coordinator;
    private final CrawlerJobLifecycleService lifecycleService;

    public CrawlerJobRunner(CrawlerTaskLogMapper jobMapper,
                            CrawlerScheduleMapper scheduleMapper,
                            CrawlerCore crawlerCore,
                            CrawlerJobCoordinator coordinator,
                            CrawlerJobLifecycleService lifecycleService) {
        this.jobMapper = jobMapper;
        this.scheduleMapper = scheduleMapper;
        this.crawlerCore = crawlerCore;
        this.coordinator = coordinator;
        this.lifecycleService = lifecycleService;
    }

    public void run(Long jobId) {
        LocalDateTime startedAt = CrawlerTime.nowUtc();
        if (jobMapper.claimQueuedJob(jobId, startedAt) != 1) {
            return;
        }

        CrawlerTaskLog claimed = jobMapper.selectById(jobId);
        if (claimed == null) {
            return;
        }
        scheduleMapper.recordJobStarted(claimed.getScheduleId(), startedAt);

        AtomicBoolean cancellation = coordinator.register(jobId);
        if (Boolean.TRUE.equals(claimed.getCancelRequested())) {
            cancellation.set(true);
        }

        CrawlExecutionSummary summary = EMPTY_SUMMARY;
        Throwable failure = null;
        try {
            summary = crawlerCore.executeCrawl(claimed.getScheduleId(), jobId, cancellation);
        } catch (Exception error) {
            failure = error;
            log.error("爬虫 Job 执行失败: jobId={}, scheduleId={}", jobId, claimed.getScheduleId(), error);
        } finally {
            try {
                lifecycleService.finish(jobId, summary, cancellation.get(), failure);
            } finally {
                coordinator.unregister(jobId);
            }
        }
    }
}
