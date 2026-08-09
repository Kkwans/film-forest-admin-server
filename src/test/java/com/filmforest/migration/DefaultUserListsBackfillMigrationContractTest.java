package com.filmforest.migration;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultUserListsBackfillMigrationContractTest {

    @Test
    void backfillsOnlyMissingDefaultListsWithoutTouchingExistingItems() throws Exception {
        try (var input = getClass().getResourceAsStream(
                "/db/migration/V17__backfill_default_user_lists.sql")) {
            assertThat(input).isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sql).contains("want_to_watch", "watching", "watched", "NOT EXISTS");
            assertThat(sql).doesNotContain("DELETE", "TRUNCATE", "DROP TABLE");
        }
    }
}
