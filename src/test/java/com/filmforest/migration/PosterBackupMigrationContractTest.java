package com.filmforest.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PosterBackupMigrationContractTest {

    @Test
    void addsSourceAndBackupSourceColumnsToEveryContentTable() throws IOException {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V35__cache_crawled_posters.sql"),
                StandardCharsets.UTF_8);

        for (String table : new String[]{"movie", "drama", "variety", "anime", "short_drama"}) {
            assertThat(sql).contains("ALTER TABLE `" + table + "`");
        }
        assertThat(sql)
                .contains("`poster_source_url`")
                .contains("`poster_backup_source_url`")
                .contains("SET `poster_source_url` = `poster_url`")
                .contains("AND `poster_url` <> ''");
    }
}
