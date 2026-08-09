package com.filmforest.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CrawlerSourceScheduleMigrationContractTest {

    @Test
    void migrationLinksOnlyPkmp4AndPreservesLegacyCronWithoutFreeTextGenres() throws IOException {
        String sql;
        try (var input = getClass().getResourceAsStream(
                "/db/migration/V9__link_crawler_sources_and_guided_schedules.sql")) {
            assertThat(input).isNotNull();
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql).contains(
                "UNIQUE KEY `uk_resource_source_code` (`code`)",
                "CREATE TABLE `crawler_source_adapter`",
                "CREATE TABLE `crawler_schedule_genre`",
                "'CUSTOM_CRON'",
                "'Asia/Shanghai'",
                "JOIN JSON_TABLE",
                "tag_type.`content_type` = schedule.`content_type`"
        );
        assertThat(sql).contains("WHEN `name` = '天堂资源' THEN 'tiantang'",
                "WHEN `name` = '非凡资源' THEN 'feifan'");
        assertThat(sql).doesNotContain("DELETE FROM", "DROP TABLE");
    }
}
