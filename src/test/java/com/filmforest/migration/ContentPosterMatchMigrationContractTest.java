package com.filmforest.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ContentPosterMatchMigrationContractTest {

    @Test
    void migrationKeepsOriginalAndTmdbPosterMetadataSideBySide() throws IOException {
        String sql;
        try (var input = getClass().getResourceAsStream(
                "/db/migration/V4__add_content_poster_match.sql")) {
            assertThat(input).isNotNull();
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql).contains("`source_poster_url`", "`tmdb_id`", "`poster_path`",
                "`poster_language`", "`confidence`", "`diagnostic`");
        assertThat(sql).contains("UNIQUE KEY `uk_content_poster_match` (`content_type`, `content_id`)");
        assertThat(sql).contains("CHECK (`confidence` IS NULL OR (`confidence` >= 0 AND `confidence` <= 1))");
        assertThat(sql).doesNotContain("DROP TABLE", "DELETE FROM", "UPDATE `movie`");
    }
}
