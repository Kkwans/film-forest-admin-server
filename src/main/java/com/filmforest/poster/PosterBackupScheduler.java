package com.filmforest.poster;

import com.filmforest.common.type.ContentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** 分批补齐历史海报，并对可恢复失败执行有界退避重试。 */
@Slf4j
@Component
public class PosterBackupScheduler {

    private static final String LOCAL_PREFIX = "/api/poster/assets/%";

    private final JdbcTemplate jdbc;
    private final PosterBackupService backupService;
    private final PosterBackupFailureRepository failureRepository;
    private final PosterBackupProperties properties;
    private final PosterBackupTaskState taskState;
    private final AtomicBoolean running = new AtomicBoolean();

    public PosterBackupScheduler(JdbcTemplate jdbc, PosterBackupService backupService,
                                 PosterBackupFailureRepository failureRepository,
                                 PosterBackupProperties properties,
                                 PosterBackupTaskState taskState) {
        this.jdbc = jdbc;
        this.backupService = backupService;
        this.failureRepository = failureRepository;
        this.properties = properties;
        this.taskState = taskState;
    }

    @Async("posterBackupExecutor")
    @Scheduled(
            fixedDelayString = "${app.poster.backup.interval:1m}",
            initialDelayString = "${app.poster.backup.initial-delay:30s}")
    public void backupPending() {
        runCycle(false);
    }

    /** 管理员显式重新检查时重新开放终止失败；定时任务不会自动重试这些记录。 */
    @Async("posterBackupExecutor")
    public void retryFailed() {
        runCycle(true);
    }

    public boolean requestStart() {
        return taskState.requestStart();
    }

    public void pause() {
        taskState.requestPause();
    }

    public PosterBackupTaskState.Snapshot snapshot() {
        return taskState.snapshot(readCatalogStats());
    }

    private void runCycle(boolean reopenTerminalFailures) {
        if (!properties.isEnabled() || !running.compareAndSet(false, true)) return;
        try {
            if (reopenTerminalFailures) failureRepository.reopenTerminalFailures();
            CatalogStats before = readCatalogStats();
            if (before.pending() == 0) {
                taskState.markNoWork(before.failed());
                return;
            }
            if (!taskState.beginCycle()) return;

            int batchSize = Math.min(100, Math.max(1, properties.getBatchSize()));
            for (ContentType type : ContentType.values()) {
                if (taskState.isPaused()) break;
                for (PendingPoster item : findEligible(type, batchSize)) {
                    if (taskState.isPaused()) break;
                    taskState.beginItem(type.value(), item.id(), item.title());
                    PosterBackupService.BackupResult result;
                    try {
                        result = backupService.backup(type, item.id(), item.sourceUrl());
                    } catch (RuntimeException error) {
                        log.warn("单张海报本地备份失败: type={}, id={}, reason={}",
                                type.value(), item.id(), error.getClass().getSimpleName());
                        result = PosterBackupService.BackupResult.retryableFailure(
                                "UNEXPECTED_ERROR", null, "海报本地化发生未预期错误");
                    }
                    if (result.success()) {
                        failureRepository.clear(type, item.id());
                    } else {
                        failureRepository.recordFailure(type, item.id(), item.sourceUrl(), result);
                    }
                    taskState.finishItem(result.success(), result.message());
                }
            }

            CatalogStats after = readCatalogStats();
            taskState.finishCycle(after.pending(), after.failed());
        } catch (RuntimeException error) {
            log.warn("海报本地备份批次失败: reason={}", error.getClass().getSimpleName());
            taskState.markError("海报回填批次失败，请查看服务日志");
        } finally {
            running.set(false);
        }
    }

    private List<PendingPoster> findEligible(ContentType type, int batchSize) {
        String table = type.value();
        return jdbc.query(
                "SELECT content.id, content.title, content.poster_source_url FROM " + table + " content "
                        + "LEFT JOIN content_poster_backup_failure failure"
                        + " ON failure.content_type = ? AND failure.content_id = content.id"
                        + " AND failure.source_url = content.poster_source_url"
                        + " WHERE content.is_deleted = 0"
                        + " AND content.poster_source_url IS NOT NULL"
                        + " AND content.poster_source_url <> ''"
                        + " AND (content.poster_url IS NULL OR content.poster_url NOT LIKE ?"
                        + " OR content.poster_backup_source_url IS NULL"
                        + " OR content.poster_backup_source_url <> content.poster_source_url)"
                        + " AND (failure.content_id IS NULL OR (failure.terminal = 0"
                        + " AND (failure.next_retry_at IS NULL OR failure.next_retry_at <= CURRENT_TIMESTAMP(3))))"
                        + " ORDER BY content.id LIMIT ?",
                (rs, rowNum) -> new PendingPoster(
                        rs.getLong("id"), rs.getString("title"), rs.getString("poster_source_url")),
                type.value(), LOCAL_PREFIX, batchSize);
    }

    private CatalogStats readCatalogStats() {
        long total = 0;
        long succeeded = 0;
        long failed = 0;
        long pending = 0;
        for (ContentType type : ContentType.values()) {
            TypeStats stats = jdbc.queryForObject(
                    "SELECT COUNT(*) total,"
                            + " COALESCE(SUM(content.poster_url LIKE ?"
                            + " AND content.poster_backup_source_url = content.poster_source_url), 0) succeeded,"
                            + " COALESCE(SUM((content.poster_url IS NULL OR content.poster_url NOT LIKE ?"
                            + " OR content.poster_backup_source_url IS NULL"
                            + " OR content.poster_backup_source_url <> content.poster_source_url)"
                            + " AND failure.terminal = 1), 0) failed,"
                            + " COALESCE(SUM((content.poster_url IS NULL OR content.poster_url NOT LIKE ?"
                            + " OR content.poster_backup_source_url IS NULL"
                            + " OR content.poster_backup_source_url <> content.poster_source_url)"
                            + " AND (failure.content_id IS NULL OR failure.terminal = 0)), 0) pending"
                            + " FROM " + type.value() + " content"
                            + " LEFT JOIN content_poster_backup_failure failure"
                            + " ON failure.content_type = ? AND failure.content_id = content.id"
                            + " AND failure.source_url = content.poster_source_url"
                            + " WHERE content.is_deleted = 0"
                            + " AND content.poster_source_url IS NOT NULL"
                            + " AND content.poster_source_url <> ''",
                    (rs, rowNum) -> new TypeStats(
                            rs.getLong("total"), rs.getLong("succeeded"),
                            rs.getLong("failed"), rs.getLong("pending")),
                    LOCAL_PREFIX, LOCAL_PREFIX, LOCAL_PREFIX, type.value());
            if (stats != null) {
                total += stats.total();
                succeeded += stats.succeeded();
                failed += stats.failed();
                pending += stats.pending();
            }
        }
        return new CatalogStats(total, succeeded, failed, pending);
    }

    public record CatalogStats(long total, long succeeded, long failed, long pending) {
    }

    private record TypeStats(long total, long succeeded, long failed, long pending) {
    }

    private record PendingPoster(long id, String title, String sourceUrl) {
    }
}
