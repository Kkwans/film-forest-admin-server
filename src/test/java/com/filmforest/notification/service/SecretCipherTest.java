package com.filmforest.notification.service;

import com.filmforest.notification.config.SecretEncryptionProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretCipherTest {

    @Test
    void encryptsWithAesGcmAndRejectsTampering() {
        String key = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
        SecretCipher cipher = new SecretCipher(new SecretEncryptionProperties(key), new SecureRandom());

        SecretCipher.EncryptedSecret encrypted = cipher.encrypt("smtp-password");

        assertThat(encrypted.ciphertext()).isNotEqualTo("smtp-password".getBytes(StandardCharsets.UTF_8));
        assertThat(encrypted.iv()).hasSize(12);
        assertThat(cipher.decrypt(encrypted.ciphertext(), encrypted.iv(), encrypted.keyVersion()))
                .isEqualTo("smtp-password");
        encrypted.ciphertext()[0] ^= 1;
        assertThatThrownBy(() -> cipher.decrypt(encrypted.ciphertext(), encrypted.iv(), encrypted.keyVersion()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("解密失败");
    }
}
