package com.filmforest.common.auth;

import com.filmforest.content.entity.PasswordAlgorithm;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordServiceTest {

    private final PasswordService passwordService = new PasswordService();

    @Test
    void verifiesCurrentBcryptWithoutMigration() {
        String hash = passwordService.encode("secret12");

        PasswordService.Verification verification = passwordService.verify(
                "secret12", hash, PasswordAlgorithm.BCRYPT);

        assertThat(verification.matches()).isTrue();
        assertThat(verification.needsUpgrade()).isFalse();
    }

    @Test
    void marksLegacySha256ForUpgradeOnlyAfterValidMatch() throws Exception {
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest("legacy12".getBytes(StandardCharsets.UTF_8)));

        assertThat(passwordService.verify("legacy12", hash, PasswordAlgorithm.LEGACY_SHA256))
                .isEqualTo(new PasswordService.Verification(true, true));
        assertThat(passwordService.verify("wrong-password", hash, PasswordAlgorithm.LEGACY_SHA256))
                .isEqualTo(new PasswordService.Verification(false, false));
    }

    @Test
    void rejectsUnknownHashFormats() {
        assertThat(passwordService.verify("secret12", "not-a-password-hash", null).matches())
                .isFalse();
    }
}
