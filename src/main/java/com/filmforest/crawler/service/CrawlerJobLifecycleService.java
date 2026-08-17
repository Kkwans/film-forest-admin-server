package com.filmforest.crawler.service;

import com.filmforest.crawler.entity.CrawlerSchedule;
import com.filmforest.crawler.entity.CrawlerCrawlMode;
import com.filmforest.crawler.entity.CrawlerConfigurationStatus;
import com.filmforest.crawler.entity.CrawlerSourceSort;
import com.filmforest.crawler.entity.CrawlerTraversalMode;
import com.filmforest.crawler.entity.CrawlerStatus;
import com.filmforest.crawler.entity.CrawlerTaskLog;
import com.filmforest.crawler.entity.CrawlerTriggerType;
import com.filmforest.crawler.core.CrawlerFetchException;
import com.filmforest.crawler.core.CrawlerSourceUnavailableException;
import com.filmforest.crawler.core.CrawlerSourceStructureException;
import com.filmforest.crawler.mapper.CrawlerScheduleMapper;
import com.filmforest.crawler.mapper.CrawlerTaskLogMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class CrawlerJobLifecycleService {

    private static final int ERROR_SUMMARY_LIMIT = 1000;

    private final CrawlerScheduleMapper scheduleMapper;
    private final CrawlerTaskLogMapper jobMapper;
    private final CrawlerJobCoordinator coordinator;
    private final ApplicationEventPublisher eventPublisher;

    public CrawlerJobLifecycleService(CrawlerScheduleMapper scheduleMapper,
                                      CrawlerTaskLogMapper jobMapper,
                                      CrawlerJobCoordinator coordinator,
                                      ApplicationEventPublisher eventPublisher) {
        this.scheduleMapper = scheduleMapper;
        this.jobMapper = jobMapper;
        this.coordinator = coordinator;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 兼容旧调用者：锁定 schedule 后原子创建唯一 QUEUED Job，并返回其 ID。
     */
    @Transactional
    public Long enqueue(Long scheduleId, CrawlerTriggerType triggerType, Long retryOfJobId) {
        CrawlerTaskLog job = enqueueJob(scheduleId, triggerType, retryOfJobId);
        return job == null ? null : job.getId();
    }

    /**
     * 锁定 schedule 后原子创建唯一 QUEUED Job，并沿调用链返回已入库 Job。
     * 事件只会在事务成功提交后派发。
     */
    @Transactional
    public CrawlerTaskLog enqueueJob(Long scheduleId, CrawlerTriggerType triggerType, Long retryOfJobId) {
        CrawlerSchedule schedule = scheduleMapper.selectByIdForUpdate(scheduleId);
        LocalDateTime now = CrawlerTime.nowUtc();
        if (schedule == null || !isScheduledTriggerStillDue(schedule, triggerType, now)
                || jobMapper.selectActiveByScheduleId(scheduleId) != null) {
            return null;
        }
        if (CrawlerConfigurationStatus.NEEDS_REVIEW.getCode()
                .equals(schedule.getConfigurationStatus())) {
            return null;
        }

        CrawlerTaskLog retriedJob = null;
        if (retryOfJobId != null) {
            retriedJob = jobMapper.selectById(retryOfJobId);
            CrawlerStatus previous = retriedJob == null ? null : CrawlerStatus.fromCode(retriedJob.getStatus());
            if (retriedJob == null || !scheduleId.equals(retriedJob.getScheduleId())
                    || previous == null || !previous.isRetryable()) {
                return null;
            }
        }

        CrawlerCrawlMode crawlMode = CrawlerCrawlMode.fromCode(
                retriedJob == null ? schedule.getCrawlMode() : retriedJob.getCrawlMode());
        if (triggerType == CrawlerTriggerType.SCHEDULED && crawlMode == CrawlerCrawlMode.FULL) {
            return null;
        }

        CrawlerTaskLog job = new CrawlerTaskLog();
        job.setScheduleId(scheduleId);
        job.setScheduleName(schedule.getName());
        job.setContentType(schedule.getContentType());
        job.setSourceCode(normalizeSourceCode(
                schedule.getAdapterCode() == null ? schedule.getSourceSite() : schedule.getAdapterCode()));
        job.setCrawlMode(crawlMode.getCode());
        String sourceSort = schedule.getSourceSort() == null
                ? CrawlerSourceSort.fromCode(schedule.getPriority()).getCode()
                : CrawlerSourceSort.fromCode(schedule.getSourceSort()).getCode();
        job.setSourceSort(sourceSort);
        String traversal = schedule.getTraversalMode();
        if (traversal == null || traversal.isBlank()) {
            traversal = crawlMode == CrawlerCrawlMode.FULL
                    ? CrawlerTraversalMode.MANUAL_FULL.getCode()
                    : CrawlerSourceSort.TIME.getCode().equals(sourceSort)
                    ? CrawlerTraversalMode.CONTINUOUS_SYNC.getCode()
                    : CrawlerTraversalMode.BACKFILL_CONTINUE.getCode();
        }
        job.setTraversalMode(traversal);
        job.setQueryProfileHash(schedule.getQueryProfileHash() == null
                ? CrawlerQueryProfile.hash(schedule) : schedule.getQueryProfileHash());
        job.setQuerySnapshot(CrawlerQueryProfile.snapshot(schedule));
        job.setSourceFilterSnapshot(CrawlerQueryProfile.filterSnapshot(schedule));
        job.setConfigSnapshot(CrawlerQueryProfile.canonical(schedule));
        job.setTriggerType(triggerType.getCode());
        job.setRetryOfJobId(retryOfJobId);
        job.setStatus(CrawlerStatus.QUEUED.getCode());
        job.setCancelRequested(false);
        boolean resumeFull = retriedJob != null && crawlMode == CrawlerCrawlMode.FULL;
        job.setCurrentPage(resumeFull && retriedJob.getCurrentPage() != null
                ? retriedJob.getCurrentPage() : 1);
        job.setCheckpoint(resumeFull ? retriedJob.getCheckpoint() : null);
        job.setDiscoveredCount(0);
        job.setFetchSucceededCount(0);
        job.setParseSucceededCount(0);
        job.setAddedCount(0);
        job.setUpdatedCount(0);
        job.setUnchangedCount(0);
        job.setFilteredCount(0);
        job.setFailedCount(0);
        job.setPagesScanned(0);
        job.setListItemsScanned(0);
        job.setDetailAttempted(0);
        job.setCursorAdvanced(0);
        job.setNewItems(0);
        job.setBackfillItems(0);
        job.setItemsCrawled(0);
        job.setItemsAdded(0);
        job.setItemsUpdated(0);
        job.setQueuedAt(now);
        job.setProgressUpdatedAt(now);
        if (jobMapper.insert(job) <= 0 || job.getId() == null) {
            return null;
        }

        if (triggerType == CrawlerTriggerType.SCHEDULED) {
            schedule.setNextRunTime(CrawlerTime.nextRunUtc(
                    schedule.getCronExpression(), now, schedule.getTimezone()));
            scheduleMapper.updateById(schedule);
        }

        eventPublisher.publishEvent(new CrawlerJobQueuedEvent(job.getId()));
        return job;
    }

    @Transactional
    public boolean requestCancelBySchedule(Long scheduleId) {
        CrawlerSchedule schedule = scheduleMapper.selectByIdForUpdate(scheduleId);
        if (schedule == null) {
            return false;
        }
        CrawlerTaskLog active = jobMapper.selectActiveByScheduleId(scheduleId);
        return active != null && requestCancel(active);
    }

    @Transactional
    public boolean requestCancelByJob(Long jobId) {
        CrawlerTaskLog job = jobMapper.selectById(jobId);
        return job != null && requestCancel(job);
    }

    private boolean requestCancel(CrawlerTaskLog job) {
        CrawlerStatus status = CrawlerStatus.fromCode(job.getStatus());
        if (status == null || !status.isActive()) {
            return false;
        }
        int updated = jobMapper.requestCancel(job.getId(), CrawlerTime.nowUtc());
        if (updated > 0 && status != CrawlerStatus.QUEUED) {
            coordinator.requestCancellation(job.getId());
        }
        return updated > 0;
    }

    @Transactional
    public void finish(Long jobId, CrawlExecutionSummary summary, boolean cancellationRequested,
                       Throwable failure) {
        CrawlerTaskLog job = jobMapper.selectById(jobId);
        if (job == null) {
            return;
        }
        CrawlerStatus current = CrawlerStatus.fromCode(job.getStatus());
        if (current != null && current.isTerminal()) {
            return;
        }

        LocalDateTime now = CrawlerTime.nowUtc();
        CrawlerStatus terminal = terminalStatus(summary, cancellationRequested || Boolean.TRUE.equals(job.getCancelRequested()), failure);
        job.setStatus(terminal.getCode());
        job.setCancelRequested(cancellationRequested || Boolean.TRUE.equals(job.getCancelRequested()));
        job.setDiscoveredCount(summary.discovered());
        job.setFetchSucceededCount(summary.fetchSucceeded());
        job.setParseSucceededCount(summary.parseSucceeded());
        job.setAddedCount(summary.added());
        job.setUpdatedCount(summary.updated());
        job.setUnchangedCount(summary.unchanged());
        job.setFilteredCount(summary.filtered());
        job.setFailedCount(summary.failed());
        job.setPagesScanned(summary.pagesScanned());
        job.setListItemsScanned(summary.listItemsScanned());
        job.setDetailAttempted(summary.detailAttempted());
        job.setCursorAdvanced(summary.cursorAdvanced());
        job.setNewItems(summary.newItems());
        job.setBackfillItems(summary.backfillItems());
        job.setOutcomeCode(outcomeCode(summary, cancellationRequested
                || Boolean.TRUE.equals(job.getCancelRequested()), failure));
        job.setItemsCrawled(summary.discovered());
        job.setItemsAdded(summary.added());
        job.setItemsUpdated(summary.updated());
        job.setCurrentItem(null);
        job.setHeartbeatAt(now);
        job.setProgressUpdatedAt(now);
        job.setFinishedAt(now);
        job.setDurationMs(durationMillis(job.getStartedAt(), now));
        if (failure != null) {
            String error = summarize(failure);
            job.setErrorSummary(error);
            job.setErrorMessage(error);
        }
        jobMapper.updateById(job);
        scheduleMapper.recordJobFinished(job.getScheduleId(), summary.discovered());
        eventPublisher.publishEvent(new CrawlerJobTerminalEvent(
                job.getId(),
                job.getScheduleId(),
                job.getScheduleName(),
                job.getSourceCode(),
                job.getContentType(),
                terminal,
                job.getRetryOfJobId(),
                summary.discovered(),
                summary.added(),
                summary.updated(),
                summary.failed()));
    }

    private CrawlerStatus terminalStatus(CrawlExecutionSummary summary, boolean cancellationRequested,
                                         Throwable failure) {
        if (cancellationRequested) {
            return CrawlerStatus.CANCELLED;
        }
        if (failure != null) {
            return CrawlerStatus.FAILED;
        }
        if (summary.failed() > 0 && summary.parseSucceeded() > 0) {
            return CrawlerStatus.PARTIAL_SUCCESS;
        }
        if (summary.failed() > 0) {
            return CrawlerStatus.FAILED;
        }
        return CrawlerStatus.SUCCESS;
    }

    private String outcomeCode(CrawlExecutionSummary summary, boolean cancellationRequested,
                               Throwable failure) {
        if (cancellationRequested) {
            return "CANCELLED";
        }
        if (failure instanceof CrawlerFetchException fetchFailure) {
            return switch (fetchFailure.getFetchResult().category()) {
                case CHALLENGE_PAGE, FORBIDDEN -> "SOURCE_UNAVAILABLE";
                case RATE_LIMITED -> "RATE_LIMITED";
                case NETWORK_ERROR, SERVER_ERROR -> "NETWORK_FAILED";
                default -> "FAILED";
            };
        }
        if (failure instanceof CrawlerSourceUnavailableException) {
            return "SOURCE_UNAVAILABLE";
        }
        if (failure instanceof CrawlerSourceStructureException) {
            return "STRUCTURE_CHANGED";
        }
        if (failure instanceof CrawlerRecoveryRequiredException) {
            return "RECOVERY_REQUIRED";
        }
        if (failure != null) {
            return "FAILED";
        }
        return summary.failed() > 0
                ? (summary.parseSucceeded() > 0 ? "PARTIAL" : "FAILED")
                : "COMPLETED";
    }

    private String normalizeSourceCode(String sourceSite) {
        return sourceSite == null || sourceSite.isBlank() ? "pkmp4" : sourceSite.trim();
    }

    private boolean isScheduledTriggerStillDue(CrawlerSchedule schedule,
                                                CrawlerTriggerType triggerType,
                                                LocalDateTime now) {
        if (triggerType != CrawlerTriggerType.SCHEDULED) {
            return true;
        }
        return Integer.valueOf(1).equals(schedule.getEnabled())
                && schedule.getNextRunTime() != null
                && !schedule.getNextRunTime().isAfter(now)
                && !CrawlerConfigurationStatus.NEEDS_REVIEW.getCode()
                .equals(schedule.getConfigurationStatus());
    }

    private long durationMillis(LocalDateTime startedAt, LocalDateTime finishedAt) {
        return startedAt == null ? 0L : Math.max(0L, Duration.between(startedAt, finishedAt).toMillis());
    }

    private String summarize(Throwable failure) {
        String message = failure.getClass().getSimpleName() + ": "
                + (failure.getMessage() == null ? "无错误详情" : failure.getMessage());
        return truncate(message);
    }

    private String truncate(String value) {
        return value == null || value.length() <= ERROR_SUMMARY_LIMIT
                ? value : value.substring(0, ERROR_SUMMARY_LIMIT);
    }
}
