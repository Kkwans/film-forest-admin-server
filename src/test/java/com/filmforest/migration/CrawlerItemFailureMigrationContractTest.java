package com.filmforest.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CrawlerItemFailureMigrationContractTest {

    @Test
    void migrationCreatesPerJobFailureIsolationWithoutChangingExistingRows() throws IOException {
        String sql;
        try (var input = getClass().getResourceAsStream(
                "/db/migration/V12__record_crawler_item_failures.sql")) {
            assertThat(input).isNotNull();
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql).contains(
                "CREATE TABLE `crawler_job_item_failure`",
                "UNIQUE KEY `uk_crawler_job_item_failure`",
                "`failure_stage` varchar(20) NOT NULL",
                "`attempt_count` int unsigned NOT NULL",
                "FOREIGN KEY (`job_id`) REFERENCES `crawler_task_log` (`id`) ON DELETE CASCADE");
        assertThat(sql).doesNotContain("DELETE FROM", "TRUNCATE ", "DROP TABLE", "UPDATE `crawler_task_log`");
    }
}
