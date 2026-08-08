package com.filmforest.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ContentTagMigrationContractTest {

    @Test
    void migrationCreatesTheTablesExpectedByBothTagServicesWithoutRewritingContent() throws IOException {
        String sql;
        try (var input = getClass().getResourceAsStream(
                "/db/migration/V6__create_content_tags.sql")) {
            assertThat(input).isNotNull();
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql).contains(
                "CREATE TABLE `tag`",
                "CREATE TABLE `content_tag`",
                "UNIQUE KEY `uk_tag_active_name` (`active_name`)",
                "UNIQUE KEY `uk_content_tag` (`content_type`, `content_id`, `tag_id`)",
                "KEY `idx_content_tag_filter` (`tag_id`, `content_type`, `content_id`)",
                "CONSTRAINT `fk_content_tag_tag`",
                "'short_drama'"
        );
        assertThat(sql).doesNotContain(
                "INSERT INTO `tag`",
                "INSERT INTO `content_tag`",
                "UPDATE `movie`",
                "DELETE FROM",
                "DROP TABLE"
        );
    }
}
