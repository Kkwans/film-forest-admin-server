package com.filmforest.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class UserPlaybackHistoryMigrationContractTest {

    @Test
    void migrationCreatesPerUserPlaybackProgressWithSafeLifecycleConstraints() throws IOException {
        String sql;
        try (var input = getClass().getResourceAsStream(
                "/db/migration/V19__create_user_playback_history.sql")) {
            assertThat(input).isNotNull();
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql).contains(
                "CREATE TABLE `user_playback_history`",
                "UNIQUE KEY `uk_user_playback_content` (`user_id`, `content_type`, `content_id`)",
                "KEY `idx_user_playback_recent` (`user_id`, `last_played_at`, `id`)",
                "REFERENCES `user` (`id`) ON DELETE CASCADE",
                "REFERENCES `resource_online` (`id`) ON DELETE SET NULL",
                "'short_drama'",
                "`position_seconds` BETWEEN 0 AND 604800");
        assertThat(sql).doesNotContain("source_url", "DROP TABLE", "TRUNCATE", "DELETE FROM");
    }
}
