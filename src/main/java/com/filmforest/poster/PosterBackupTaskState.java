package com.filmforest.poster;

import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 海报回填的进程内任务状态。
 *
 * <p>状态用于管理端实时展示和暂停边界；实际待处理数量仍以数据库为准，
 * 因此服务重启后可以继续从数据库未本地化的记录开始。</p>
 */
@Component
public class PosterBackupTaskState {

    public enum Status {
        IDLE,
        RUNNING,
        PAUSED,
        COMPLETED,
        ERROR
    }

    private final PosterBackupProperties properties;
    private Status status = Status.IDLE;
    private long total;
    private long pending;
    private long processed;
    private long succeeded;
    private long failed;
    private String currentContentType;
    private Long currentId;
    private String currentTitle;
    private String lastError;
    private Instant startedAt;
    private Instant updatedAt = Instant.now();
    private Instant completedAt;

    public PosterBackupTaskState(PosterBackupProperties properties) {
        this.properties = properties;
    }

    /** 请求启动或从暂停状态继续；已在运行时不会重复排队。 */
    public synchronized boolean requestStart() {
        if (status == Status.RUNNING) return false;
        if (status == Status.COMPLETED || status == Status.ERROR) {
            resetCounters();
        }
        status = Status.RUNNING;
        if (startedAt == null) startedAt = Instant.now();
        completedAt = null;
        lastError = null;
        touch();
        return true;
    }

    public synchronized boolean requestPause() {
        if (status != Status.RUNNING) return false;
        status = Status.PAUSED;
        touch();
        return true;
    }

    /** 调度器开始一个扫描周期；暂停状态不会被定时器悄悄恢复。 */
    public synchronized boolean beginCycle(long pendingCount) {
        if (status == Status.PAUSED) return false;
        if (status == Status.COMPLETED) {
            resetCounters();
            startedAt = Instant.now();
        }
        if (startedAt == null) startedAt = Instant.now();
        total = Math.max(total, succeeded + Math.max(0, pendingCount));
        if (total == 0) total = Math.max(0, pendingCount);
        pending = Math.max(0, pendingCount);
        status = Status.RUNNING;
        completedAt = null;
        touch();
        return true;
    }

    public synchronized boolean isPaused() {
        return status == Status.PAUSED;
    }

    public synchronized void beginItem(String contentType, long id, String title) {
        currentContentType = contentType;
        currentId = id;
        currentTitle = title;
        touch();
    }

    public synchronized void finishItem(boolean success, String errorMessage) {
        processed++;
        if (success) {
            succeeded++;
            pending = Math.max(0, pending - 1);
        } else {
            failed++;
            if (errorMessage != null && !errorMessage.isBlank()) lastError = errorMessage;
        }
        currentContentType = null;
        currentId = null;
        currentTitle = null;
        touch();
    }

    public synchronized void finishCycle(long pendingCount) {
        pending = Math.max(0, pendingCount);
        currentContentType = null;
        currentId = null;
        currentTitle = null;
        if (status != Status.PAUSED) {
            if (pending == 0) {
                status = Status.COMPLETED;
                completedAt = Instant.now();
            } else {
                status = Status.RUNNING;
            }
        }
        touch();
    }

    public synchronized void markNoWork() {
        if (status == Status.PAUSED) return;
        pending = 0;
        currentContentType = null;
        currentId = null;
        currentTitle = null;
        status = Status.COMPLETED;
        completedAt = Instant.now();
        touch();
    }

    public synchronized void markError(String message) {
        status = Status.ERROR;
        currentContentType = null;
        currentId = null;
        currentTitle = null;
        lastError = message;
        touch();
    }

    public synchronized Snapshot snapshot() {
        double progressPercent = total <= 0
                ? (status == Status.COMPLETED ? 100.0 : 0.0)
                : Math.min(100.0, Math.max(0.0, (total - pending) * 100.0 / total));
        long rateWindowSeconds = properties.getRateLimitWindow() == null
                ? 5L : Math.max(1L, properties.getRateLimitWindow().toSeconds());
        return new Snapshot(
                status.name(), total, pending, processed, succeeded, failed, progressPercent,
                currentContentType, currentId, currentTitle, lastError,
                startedAt, updatedAt, completedAt,
                properties.getMaxRequestsPerWindow(), rateWindowSeconds);
    }

    private void resetCounters() {
        total = 0;
        pending = 0;
        processed = 0;
        succeeded = 0;
        failed = 0;
        currentContentType = null;
        currentId = null;
        currentTitle = null;
        lastError = null;
        startedAt = null;
        completedAt = null;
    }

    private void touch() {
        updatedAt = Instant.now();
    }

    public record Snapshot(
            String status,
            long total,
            long pending,
            long processed,
            long succeeded,
            long failed,
            double progressPercent,
            String currentContentType,
            Long currentId,
            String currentTitle,
            String lastError,
            Instant startedAt,
            Instant updatedAt,
            Instant completedAt,
            int maxRequestsPerWindow,
            long rateLimitWindowSeconds) {
    }
}
