package com.filmforest.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CanonicalContentIdentityMigrationContractTest {

    @Test
    void migrationSeparatesSourceIdsFromCanonicalContentWithoutRewritingContentRows()
            throws IOException {
        String sql;
        try (var input = getClass().getResourceAsStream(
                "/db/migration/V11__establish_canonical_content_identity.sql")) {
            assertThat(input).isNotNull();
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql).contains(
                "CREATE TABLE `crawler_content_identity`",
                "UNIQUE KEY `uk_crawler_content_identity` (`content_type`, `canonical_key`)",
                "ADD COLUMN `canonical_key` char(64)",
                "KEY `idx_source_item_canonical` (`content_type`, `canonical_key`)");
        assertThat(sql).doesNotContain(
                "UPDATE `movie`", "UPDATE `drama`", "DELETE FROM", "TRUNCATE ");
    }
}
