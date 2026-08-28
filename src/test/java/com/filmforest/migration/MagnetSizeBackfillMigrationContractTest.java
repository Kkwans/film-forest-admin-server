package com.filmforest.migration;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MagnetSizeBackfillMigrationContractTest {

    @Test
    void migrationBackfillsDeclaredSizesWithoutTreatingResolutionAsFileSize() throws Exception {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V31__backfill_declared_magnet_sizes.sql"),
                StandardCharsets.UTF_8);

        assertThat(sql).contains("UPDATE `resource_magnet`", "COALESCE(NULLIF(`raw_text`, ''), `title`)",
                "REGEXP_LIKE", "排除裸 K 单位", "size_bytes` IS NULL");
        assertThat(sql).doesNotContain("DROP TABLE", "DELETE FROM");
    }
}
