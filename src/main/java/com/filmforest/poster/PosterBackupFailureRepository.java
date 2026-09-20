package com.filmforest.poster;

import com.filmforest.common.type.ContentType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** 持久化当前海报源的失败状态、重试次数和退避边界。 */
@Repository
public class PosterBackupFailureRepository {

    private final JdbcTemplate jdbc;
    private final PosterBackupProperties properties;

    public PosterBackupFailureRepository(JdbcTemplate jdbc, PosterBackupProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    public FailureState recordFailure(ContentType type, long contentId, String sourceUrl,
                                      PosterBackupService.BackupResult result) {
        FailureState previous = find(type, contentId).stream().findFirst().orElse(null);
        boolean sameSource = previous != null && previous.sourceUrl().equals(sourceUrl);
        int attempts = sameSource ? previous.attemptCount() + 1 : 1;
        boolean terminal = result.terminal() || attempts >= Math.max(1, properties.getMaxAttempts());
        Instant now = Instant.now();
        Instant firstFailedAt = sameSource ? previous.firstFailedAt() : now;
        Instant nextRetryAt = terminal ? null : now.plus(retryDelay(attempts));

        int updated = jdbc.update("""
                UPDATE content_poster_backup_failure
                   SET source_url = ?, failure_code = ?, http_status = ?, attempt_count = ?,
                       terminal = ?, next_retry_at = ?, last_error = ?,
                       first_failed_at = ?, last_failed_at = ?
                 WHERE content_type = ? AND content_id = ?
                """, sourceUrl, result.failureCode(), result.httpStatus(), attempts,
                terminal, nextRetryAt, result.message(), firstFailedAt, now,
                type.value(), contentId);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO content_poster_backup_failure
                        (content_type, content_id, source_url, failure_code, http_status,
                         attempt_count, terminal, next_retry_at, last_error,
                         first_failed_at, last_failed_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, type.value(), contentId, sourceUrl, result.failureCode(),
                    result.httpStatus(), attempts, terminal, nextRetryAt, result.message(),
                    firstFailedAt, now);
        }
        return new FailureState(sourceUrl, attempts, terminal, nextRetryAt, firstFailedAt);
    }

    public void clear(ContentType type, long contentId) {
        jdbc.update("DELETE FROM content_poster_backup_failure WHERE content_type = ? AND content_id = ?",
                type.value(), contentId);
    }

    /** 管理员显式点击重新检查时，允许所有当前终止失败再尝试一次。 */
    public void reopenTerminalFailures() {
        jdbc.update("""
                UPDATE content_poster_backup_failure
                   SET terminal = 0, attempt_count = 1, next_retry_at = CURRENT_TIMESTAMP(3)
                 WHERE terminal = 1
                """);
    }

    private List<FailureState> find(ContentType type, long contentId) {
        return jdbc.query("""
                        SELECT source_url, attempt_count, terminal, next_retry_at, first_failed_at
                          FROM content_poster_backup_failure
                         WHERE content_type = ? AND content_id = ?
                        """,
                (rs, rowNum) -> new FailureState(
                        rs.getString("source_url"),
                        rs.getInt("attempt_count"),
                        rs.getBoolean("terminal"),
                        rs.getTimestamp("next_retry_at") == null
                                ? null : rs.getTimestamp("next_retry_at").toInstant(),
                        rs.getTimestamp("first_failed_at").toInstant()),
                type.value(), contentId);
    }

    private Duration retryDelay(int attempts) {
        Duration initial = properties.getRetryInitialDelay();
        Duration maximum = properties.getRetryMaxDelay();
        long multiplier = 1L << Math.min(20, Math.max(0, attempts - 1));
        try {
            Duration delay = initial.multipliedBy(multiplier);
            return delay.compareTo(maximum) > 0 ? maximum : delay;
        } catch (ArithmeticException overflow) {
            return maximum;
        }
    }

    record FailureState(String sourceUrl, int attemptCount, boolean terminal,
                        Instant nextRetryAt, Instant firstFailedAt) {
    }
}
