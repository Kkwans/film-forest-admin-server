package com.filmforest.crawler.scheduler;

import com.filmforest.crawler.config.CrawlerExecutionProperties;
import com.filmforest.crawler.entity.CrawlerTaskLog;
import com.filmforest.crawler.mapper.CrawlerTaskLogMapper;
import com.filmforest.crawler.service.CrawlerJobCoordinator;
import com.filmforest.crawler.service.CrawlerJobDispatcher;
import com.filmforest.crawler.service.CrawlerTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
public class CrawlerJobMaintenance {

    private final CrawlerTaskLogMapper jobMapper;
    private final CrawlerJobCoordinator coordinator;
    private final CrawlerJobDispatcher dispatcher;
    private final CrawlerExecutionProperties properties;

    public CrawlerJobMaintenance(CrawlerTaskLogMapper jobMapper,
                                 CrawlerJobCoordinator coordinator,
                                 CrawlerJobDispatcher dispatcher,
                                 CrawlerExecutionProperties properties) {
        this.jobMapper = jobMapper;
        this.coordinator = coordinator;
        this.dispatcher = dispatcher;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverAfterStartup() {
        recoverStaleJobs();
        dispatchQueuedJobs();
    }

    /**
     * 周期补位持久化队列；Coordinator 保证同一 Job 不会重复进入内存队列。
     */
    @Scheduled(fixedDelayString = "${app.crawler.execution.queue-dispatch-interval-ms:5000}")
    public void dispatchQueuedJobs() {
        for (CrawlerTaskLog queued : jobMapper.selectQueuedJobs(1000)) {
            dispatcher.dispatchExisting(queued.getId());
        }
    }

    @Scheduled(fixedDelayString = "${app.crawler.execution.heartbeat-interval-ms:15000}")
    public void heartbeatRunningJobs() {
        LocalDateTime now = CrawlerTime.nowUtc();
        for (Long jobId : coordinator.runningJobIds()) {
            jobMapper.touchHeartbeat(jobId, now);
        }
    }

    @Scheduled(fixedDelayString = "${app.crawler.execution.recovery-interval-ms:60000}")
    public void recoverStaleJobs() {
        LocalDateTime now = CrawlerTime.nowUtc();
        LocalDateTime staleBefore = now.minusNanos(Math.max(1, properties.getStaleHeartbeatMs()) * 1_000_000);
        int interrupted = jobMapper.interruptStaleJobs(staleBefore, now);
        if (interrupted > 0) {
            log.warn("已将 {} 个心跳过期的爬虫 Job 标记为 interrupted", interrupted);
        }
    }
}
