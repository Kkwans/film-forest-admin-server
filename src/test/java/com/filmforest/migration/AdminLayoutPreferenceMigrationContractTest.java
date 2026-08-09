package com.filmforest.migration;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AdminLayoutPreferenceMigrationContractTest {

    @Test
    void migrationAddsBoundedPerUserSidebarPreference() throws Exception {
        try (var input = getClass().getResourceAsStream(
                "/db/migration/V14__persist_admin_layout_preference.sql")) {
            assertThat(input).isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sql).contains(
                    "`admin_sidebar_collapsed` tinyint NOT NULL DEFAULT 0",
                    "CHECK (`admin_sidebar_collapsed` IN (0, 1))");
            assertThat(sql).doesNotContain("DROP TABLE", "TRUNCATE", "DELETE FROM");
        }
    }
}
