package com.filmforest.migration;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationOutboxMigrationContractTest {

    @Test
    void migrationCreatesPrivateInboxPreferencesEncryptedSmtpAndRetryableOutbox() throws Exception {
        try (var input = getClass().getResourceAsStream(
                "/db/migration/V13__add_notifications_and_mail_outbox.sql")) {
            assertThat(input).isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sql).contains(
                    "CREATE TABLE `admin_notification`",
                    "CREATE TABLE `admin_notification_preference`",
                    "CREATE TABLE `smtp_setting`",
                    "`password_ciphertext` varbinary(4096)",
                    "CREATE TABLE `mail_outbox`",
                    "UNIQUE KEY `uk_mail_outbox_idempotency`",
                    "`attempt_count` BETWEEN 0 AND 5");
            assertThat(sql).doesNotContain("password_plaintext", "DROP TABLE", "TRUNCATE");
        }
    }
}
