package com.filmforest.migration;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ResolvedPlaybackSourceMigrationContractTest {

    @Test
    void migrationSeparatesPlaybackUrlFromSourcePageAndBackfillsLegacyRows() throws Exception {
        try (var input = getClass().getResourceAsStream(
                "/db/migration/V15__store_resolved_playback_sources.sql")) {
            assertThat(input).isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sql).contains(
                    "`source_page_url` varchar(1000)",
                    "`playback_type` varchar(20)",
                    "'HLS', 'VIDEO', 'EMBED', 'EXTERNAL_PAGE'",
                    "SET `source_page_url` = `source_url`");
            assertThat(sql).doesNotContain("DROP TABLE", "TRUNCATE", "DELETE FROM");
        }
    }
}
