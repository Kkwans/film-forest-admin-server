package com.filmforest.notification.service;

import com.filmforest.notification.config.SecretEncryptionProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

@Component
public class SecretCipher {
    private static final int KEY_VERSION = 1;
    private static final int TAG_BITS = 128;
    private static final byte[] AAD = "film-forest:smtp:1".getBytes(StandardCharsets.UTF_8);
    private final SecretEncryptionProperties properties;
    private final SecureRandom secureRandom;

    public SecretCipher(SecretEncryptionProperties properties) {
        this(properties, new SecureRandom());
    }

    SecretCipher(SecretEncryptionProperties properties, SecureRandom secureRandom) {
        this.properties = properties;
        this.secureRandom = secureRandom;
    }

    public EncryptedSecret encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) throw new IllegalArgumentException("SMTP 密码不能为空");
        byte[] iv = new byte[12];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, properties.encryptionKey(), new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(AAD);
            return new EncryptedSecret(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)), iv, KEY_VERSION);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SMTP 密码加密失败", exception);
        }
    }

    public String decrypt(byte[] ciphertext, byte[] iv, Integer keyVersion) {
        if (ciphertext == null || iv == null) throw new IllegalStateException("SMTP 密码未配置");
        if (keyVersion == null || keyVersion != KEY_VERSION) throw new IllegalStateException("不支持的 SMTP 密钥版本");
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, properties.encryptionKey(), new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(AAD);
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SMTP 密码解密失败", exception);
        }
    }

    public record EncryptedSecret(byte[] ciphertext, byte[] iv, int keyVersion) {
    }
}
