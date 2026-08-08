package com.filmforest.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class UserPosterPreferenceMigrationContractTest {

    @Test
    void migrationStoresOnlyEncryptedCredentialsAndManualJobState() throws IOException {
        String sql;
        try (var input = getClass().getResourceAsStream(
                "/db/migration/V7__add_user_poster_preferences.sql")) {
            assertThat(input).isNotNull();
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql).contains(
                "CREATE TABLE `user_poster_setting`",
                "`credential_ciphertext` varbinary(2048)",
                "`credential_iv` varbinary(32)",
                "CHECK (`poster_source` IN ('original', 'tmdb'))",
                "CREATE TABLE `poster_enrichment_job`",
                "UNIQUE KEY `uk_poster_enrichment_active_user` (`active_user_id`)",
                "'cancel_requested'",
                "'short_drama'"
        );
        assertThat(sql).doesNotContain(
                "api_key varchar",
                "credential_plaintext",
                "INSERT INTO `user_poster_setting`",
                "UPDATE `movie`",
                "DELETE FROM",
                "DROP TABLE"
        );

        String enrichmentJobSql = sql.substring(sql.indexOf("CREATE TABLE `poster_enrichment_job`"));
        assertThat(enrichmentJobSql)
                .contains("FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)")
                .doesNotContain("ON DELETE CASCADE");
    }
}
