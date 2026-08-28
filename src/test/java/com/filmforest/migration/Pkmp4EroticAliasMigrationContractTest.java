package com.filmforest.migration;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class Pkmp4EroticAliasMigrationContractTest {

    @Test
    void migrationMapsPkmp4EroticSourceWordToStandardEroticTag() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V30__fix_pkmp4_erotic_genre_alias.sql"),
                StandardCharsets.UTF_8);

        assertThat(sql).contains("'pkmp4', 'movie', '情色'");
        assertThat(sql).contains("WHERE `code` = 'erotic'");
        assertThat(sql).contains("INSERT IGNORE");
    }
}
