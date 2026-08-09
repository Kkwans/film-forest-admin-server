package com.filmforest.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class OperationsQueryIndexesMigrationContractTest {

    @Test
    void migrationAddsOnlyTheIndexesNeededByHighFrequencyQueries() throws IOException {
        String sql;
        try (var input = getClass().getResourceAsStream(
                "/db/migration/V18__optimize_catalog_and_operations_queries.sql")) {
            assertThat(input).isNotNull();
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql).contains(
                "`idx_movie_public_catalog` (`is_deleted`, `status`, `updated_at`, `id`)",
                "`idx_short_drama_public_catalog` (`is_deleted`, `status`, `updated_at`, `id`)",
                "`idx_resource_online_recent` (`is_deleted`, `created_at`, `id`)",
                "`idx_resource_magnet_active_recent`",
                "`idx_resource_cloud_active_recent`",
                "`idx_crawler_job_queue` (`status`, `queued_at`, `id`)",
                "`idx_crawler_job_page` (`queued_at`, `id`)",
                "`idx_admin_notification_recent` (`user_id`, `created_at`, `id`)");
        assertThat(sql).doesNotContain("DELETE FROM", "DROP TABLE", "DROP INDEX", "TRUNCATE");
    }
}
