package com.filmforest.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SourceItemResourceDiffMigrationContractTest {

    @Test
    void migrationCreatesUniqueSourceMappingWithoutRewritingHistoricalResources() throws IOException {
        String sql;
        try (var input = getClass().getResourceAsStream(
                "/db/migration/V5__establish_source_item_and_resource_diff.sql")) {
            assertThat(input).isNotNull();
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql).contains("CREATE TABLE `crawler_source_item`",
                "UNIQUE KEY `uk_crawler_source_item` (`source_code`, `content_type`, `external_id`)",
                "`list_fingerprint` char(64)", "`detail_fingerprint` char(64)",
                "`last_parse_status` varchar(32)");
        assertThat(sql).contains("ALTER TABLE `resource_magnet`",
                "ALTER TABLE `resource_cloud`", "ALTER TABLE `resource_online`",
                "`resource_key` char(64)", "`last_seen_at` datetime(6)",
                "`removed_at` datetime(6)");
        assertThat(sql).doesNotContain("DELETE FROM `resource_", "DROP TABLE",
                "resource_key` char(64) NOT NULL");
    }
}
