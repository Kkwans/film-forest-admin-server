package com.filmforest.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PosterBackupFailureCollationMigrationContractTest {

    @Test
    void alignsFailureSourceUrlWithContentTableCollation() throws IOException {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V37__align_poster_failure_collation.sql"),
                StandardCharsets.UTF_8);

        assertThat(sql)
                .contains("ALTER TABLE `content_poster_backup_failure`")
                .contains("CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci")
                .doesNotContain("DROP TABLE", "DELETE FROM", "UPDATE `movie`");
    }
}
