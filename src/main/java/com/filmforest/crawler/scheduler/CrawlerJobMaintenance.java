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
import java.util.Set;

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
        LocalDateTime heartbeatBefore = now.minusNanos(
                Math.max(1, properties.getStaleHeartbeatMs()) * 1_000_000);
        LocalDateTime progressBefore = now.minusNanos(
                Math.max(1, properties.getStalledProgressMs()) * 1_000_000);
        Set<Long> runningJobIds = coordinator.runningJobIds();
        int heartbeatExpired = interruptHeartbeatExpiredJobs(
                heartbeatBefore, now, runningJobIds);
        int progressStalled = cancelProgressStalledJobs(progressBefore, runningJobIds);
        if (heartbeatExpired > 0) {
            log.warn("已将 {} 个心跳过期的爬虫 Job 标记为 interrupted", heartbeatExpired);
        }
        if (progressStalled > 0) {
            log.warn("已请求取消 {} 个进度停滞的爬虫 Job", progressStalled);
        }
    }

    private int interruptHeartbeatExpiredJobs(LocalDateTime staleBefore, LocalDateTime now,
                                              Set<Long> runningJobIds) {
        int interrupted = 0;
        for (Long jobId : jobMapper.selectHeartbeatExpiredJobIds(staleBefore)) {
            if (jobMapper.interruptHeartbeatExpiredJob(jobId, staleBefore, now) == 1) {
                interrupted++;
                signalRunningWorker(jobId, runningJobIds);
            }
        }
        return interrupted;
    }

    private int cancelProgressStalledJobs(LocalDateTime stalledBefore,
                                          Set<Long> runningJobIds) {
        int cancellationRequested = 0;
        for (Long jobId : jobMapper.selectProgressStalledJobIds(stalledBefore)) {
            if (jobMapper.requestProgressStalledCancellation(jobId, stalledBefore) == 1) {
                cancellationRequested++;
                signalRunningWorker(jobId, runningJobIds);
            }
        }
        return cancellationRequested;
    }

    private void signalRunningWorker(Long jobId, Set<Long> runningJobIds) {
        if (runningJobIds.contains(jobId)) {
            coordinator.requestCancellation(jobId);
        }
    }
}
