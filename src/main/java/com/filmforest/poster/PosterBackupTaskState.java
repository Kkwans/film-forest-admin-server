package com.filmforest.poster;

import org.springframework.stereotype.Component;

import java.time.Instant;

/** 只保存当前进程的运行状态；海报总量和唯一结果始终从数据库实时计算。 */
@Component
public class PosterBackupTaskState {

    public enum Status {
        IDLE,
        RUNNING,
        PAUSED,
        COMPLETED,
        COMPLETED_WITH_FAILURES,
        ERROR
    }

    private final PosterBackupProperties properties;
    private Status status = Status.IDLE;
    private long attempted;
    private long successfulAttempts;
    private long failedAttempts;
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

    public synchronized boolean requestStart() {
        if (status == Status.RUNNING) return false;
        if (status == Status.COMPLETED || status == Status.COMPLETED_WITH_FAILURES || status == Status.ERROR) {
            resetAttempts();
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

    public synchronized boolean beginCycle() {
        if (status == Status.PAUSED) return false;
        if (startedAt == null) startedAt = Instant.now();
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
        attempted++;
        if (success) {
            successfulAttempts++;
        } else {
            failedAttempts++;
            if (errorMessage != null && !errorMessage.isBlank()) lastError = errorMessage;
        }
        clearCurrent();
        touch();
    }

    public synchronized void finishCycle(long pendingCount, long failedCount) {
        clearCurrent();
        if (status != Status.PAUSED) {
            if (pendingCount == 0) {
                status = failedCount > 0 ? Status.COMPLETED_WITH_FAILURES : Status.COMPLETED;
                completedAt = Instant.now();
            } else {
                status = Status.RUNNING;
            }
        }
        touch();
    }

    public synchronized void markNoWork(long failedCount) {
        if (status == Status.PAUSED) return;
        clearCurrent();
        status = failedCount > 0 ? Status.COMPLETED_WITH_FAILURES : Status.COMPLETED;
        completedAt = Instant.now();
        touch();
    }

    public synchronized void markError(String message) {
        status = Status.ERROR;
        clearCurrent();
        lastError = message;
        touch();
    }

    public synchronized Snapshot snapshot(PosterBackupScheduler.CatalogStats stats) {
        double progressPercent = stats.total() <= 0
                ? 100.0
                : Math.min(100.0, Math.max(0.0, stats.succeeded() * 100.0 / stats.total()));
        long rateWindowSeconds = properties.getRateLimitWindow() == null
                ? 5L : Math.max(1L, properties.getRateLimitWindow().toSeconds());
        return new Snapshot(
                status.name(), stats.total(), stats.pending(), stats.succeeded() + stats.failed(),
                stats.succeeded(), stats.failed(), attempted, successfulAttempts, failedAttempts,
                progressPercent, currentContentType, currentId, currentTitle, lastError,
                startedAt, updatedAt, completedAt,
                properties.getMaxRequestsPerWindow(), rateWindowSeconds);
    }

    private void resetAttempts() {
        attempted = 0;
        successfulAttempts = 0;
        failedAttempts = 0;
        clearCurrent();
        lastError = null;
        startedAt = null;
        completedAt = null;
    }

    private void clearCurrent() {
        currentContentType = null;
        currentId = null;
        currentTitle = null;
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
            long attempted,
            long successfulAttempts,
            long failedAttempts,
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
