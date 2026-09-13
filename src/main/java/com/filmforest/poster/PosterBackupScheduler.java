package com.filmforest.poster;

import com.filmforest.common.type.ContentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** 分批补齐历史海报，并重试爬虫新增或来源变更的海报。 */
@Slf4j
@Component
public class PosterBackupScheduler {

    private final JdbcTemplate jdbc;
    private final PosterBackupService backupService;
    private final PosterBackupProperties properties;
    private final PosterBackupTaskState taskState;
    private final AtomicBoolean running = new AtomicBoolean();

    public PosterBackupScheduler(JdbcTemplate jdbc, PosterBackupService backupService,
                                 PosterBackupProperties properties) {
        this(jdbc, backupService, properties, new PosterBackupTaskState(properties));
    }

    @Autowired
    public PosterBackupScheduler(JdbcTemplate jdbc, PosterBackupService backupService,
                                 PosterBackupProperties properties,
                                 PosterBackupTaskState taskState) {
        this.jdbc = jdbc;
        this.backupService = backupService;
        this.properties = properties;
        this.taskState = taskState;
    }

    @Async("posterBackupExecutor")
    @Scheduled(
            fixedDelayString = "${app.poster.backup.interval:1m}",
            initialDelayString = "${app.poster.backup.initial-delay:30s}")
    public void backupPending() {
        if (!properties.isEnabled() || !running.compareAndSet(false, true)) return;
        try {
            long pendingBefore = countPending();
            if (pendingBefore == 0) {
                taskState.markNoWork();
                return;
            }
            if (!taskState.beginCycle(pendingBefore)) return;

            int batchSize = Math.min(100, Math.max(1, properties.getBatchSize()));
            for (ContentType type : ContentType.values()) {
                if (taskState.isPaused()) break;
                List<PendingPoster> pending = jdbc.query(
                        "SELECT id, title, poster_source_url FROM " + type.value()
                                + " WHERE is_deleted = 0"
                                + " AND poster_source_url IS NOT NULL"
                                + " AND poster_source_url <> ''"
                                + " AND (poster_url IS NULL OR poster_url NOT LIKE '/api/poster/assets/%'"
                                + " OR poster_backup_source_url IS NULL"
                                + " OR poster_backup_source_url <> poster_source_url)"
                                + " ORDER BY id LIMIT ?",
                        (resultSet, rowNum) -> new PendingPoster(
                                resultSet.getLong("id"), resultSet.getString("title"),
                                resultSet.getString("poster_source_url")),
                        batchSize);
                for (PendingPoster item : pending) {
                    if (taskState.isPaused()) break;
                    taskState.beginItem(type.value(), item.id(), item.title());
                    boolean success = false;
                    try {
                        success = backupService.backup(type, item.id(), item.sourceUrl());
                    } catch (RuntimeException error) {
                        // 单张海报的数据库/文件异常不应跳过本轮其他内容。
                        log.warn("单张海报本地备份失败: type={}, id={}, reason={}",
                                type.value(), item.id(), error.getClass().getSimpleName());
                    }
                    taskState.finishItem(success, success ? null
                            : type.value() + " #" + item.id() + " 下载失败，将在下一轮重试");
                }
            }

            taskState.finishCycle(countPending());
        } catch (RuntimeException error) {
            // 部署尚未执行 V35 或共享目录暂不可用时，不影响爬虫主流程；下一轮继续检查。
            log.warn("海报本地备份批次失败: reason={}", error.getClass().getSimpleName());
            taskState.markError("海报回填批次失败，请查看服务日志");
        } finally {
            running.set(false);
        }
    }

    private long countPending() {
        long total = 0;
        for (ContentType type : ContentType.values()) {
            Long count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + type.value()
                            + " WHERE is_deleted = 0"
                            + " AND poster_source_url IS NOT NULL"
                            + " AND poster_source_url <> ''"
                            + " AND (poster_url IS NULL OR poster_url NOT LIKE '/api/poster/assets/%'"
                            + " OR poster_backup_source_url IS NULL"
                            + " OR poster_backup_source_url <> poster_source_url)",
                    Long.class);
            total += count == null ? 0 : count;
        }
        return total;
    }

    private record PendingPoster(long id, String title, String sourceUrl) {}
}
