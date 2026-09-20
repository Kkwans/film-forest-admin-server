package com.filmforest.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PosterBackupFailureMigrationContractTest {

    @Test
    void storesSourceScopedFailureAndRetryStateWithoutChangingContentRows() throws IOException {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V36__track_poster_backup_failures.sql"),
                StandardCharsets.UTF_8);

        assertThat(sql)
                .contains("CREATE TABLE `content_poster_backup_failure`")
                .contains("PRIMARY KEY (`content_type`, `content_id`)")
                .contains("`source_url` varchar(1000) NOT NULL")
                .contains("`attempt_count` int unsigned NOT NULL DEFAULT 1")
                .contains("`terminal` tinyint(1) NOT NULL DEFAULT 0")
                .contains("`next_retry_at` datetime(3) DEFAULT NULL")
                .contains("KEY `idx_poster_backup_failure_retry` (`terminal`, `next_retry_at`)")
                .doesNotContain("ALTER TABLE `movie`", "UPDATE `movie`", "DELETE FROM");
    }
}
