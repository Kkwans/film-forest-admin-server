package com.filmforest.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class StandardGenreAndContentStatusMigrationContractTest {

    @Test
    void migrationPreservesOldOfflineMeaningAndCreatesStandardGenreRelations() throws IOException {
        String sql;
        try (var input = getClass().getResourceAsStream(
                "/db/migration/V8__standardize_content_status_and_genres.sql")) {
            assertThat(input).isNotNull();
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql).contains(
                "UPDATE `movie` SET `status` = 2 WHERE `status` = 0",
                "DEFAULT 0",
                "CHECK (`status` IN (0, 1, 2))",
                "CREATE TABLE `tag_content_type`",
                "CREATE TABLE `tag_source_alias`",
                "UNIQUE KEY `uk_tag_code` (`code`)",
                "UNIQUE KEY `uk_tag_source_alias` (`source_code`, `content_type`, `alias`)",
                "('科幻', 'science-fiction'",
                "('真人秀', 'reality-show'"
        );
        assertThat(sql).doesNotContain("DELETE FROM", "DROP TABLE");
    }
}
