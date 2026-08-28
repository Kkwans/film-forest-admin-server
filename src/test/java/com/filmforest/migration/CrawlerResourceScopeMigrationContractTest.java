package com.filmforest.migration;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CrawlerResourceScopeMigrationContractTest {

    @Test
    void migrationAddsScopedJobsAndMagnetSizeWithoutDroppingExistingData() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V29__scope_crawler_resources_and_store_magnet_size.sql"),
                StandardCharsets.UTF_8);

        assertThat(sql).contains("ADD COLUMN `resource_scope` varchar(20) NOT NULL DEFAULT 'DOWNLOADS'");
        assertThat(sql).contains("ADD COLUMN `size_bytes` bigint unsigned DEFAULT NULL");
        assertThat(sql).contains("UPDATE `crawler_task_log` job");
        assertThat(sql).contains("不自动删除列");
    }
}
