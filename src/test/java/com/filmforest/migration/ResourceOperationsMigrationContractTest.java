package com.filmforest.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceOperationsMigrationContractTest {

    @Test
    void migrationAddsOperationalStateAndBoundedListIndexesWithoutDeletingData() throws IOException {
        String sql;
        try (var input = getClass().getResourceAsStream(
                "/db/migration/V10__support_resource_operations_queries.sql")) {
            assertThat(input).isNotNull();
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql).contains(
                "`idx_resource_online_admin_list`",
                "`idx_resource_magnet_admin_list`",
                "`idx_resource_cloud_admin_list`",
                "ADD COLUMN `enabled` tinyint NOT NULL DEFAULT 1",
                "SET `content_type` = 'short_drama'");
        assertThat(sql).doesNotContain("DELETE FROM", "DROP TABLE", "TRUNCATE");
    }
}
