package com.filmforest.migration;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrationInvitationMigrationContractTest {

    @Test
    void storesOnlyHashedSingleUseInvitationsWithExpiryIndexes() throws Exception {
        try (var input = getClass().getResourceAsStream(
                "/db/migration/V16__add_registration_invitations.sql")) {
            assertThat(input).isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sql).contains(
                    "`token_hash` char(64)",
                    "UNIQUE KEY `uk_registration_invitation_token_hash`",
                    "`expires_at` datetime NOT NULL",
                    "`used_by` bigint DEFAULT NULL",
                    "`revoked_at` datetime DEFAULT NULL");
            assertThat(sql).doesNotContain("`token` varchar", "DROP TABLE", "TRUNCATE");
        }
    }
}
