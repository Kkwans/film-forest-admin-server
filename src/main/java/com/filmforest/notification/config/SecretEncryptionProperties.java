package com.filmforest.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@ConfigurationProperties(prefix = "app.security")
public record SecretEncryptionProperties(String credentialEncryptionKey) {

    public SecretKey encryptionKey() {
        if (credentialEncryptionKey == null || credentialEncryptionKey.isBlank()) {
            throw new IllegalStateException("未配置 FILM_FOREST_POSTER_CREDENTIAL_KEY，无法保存或读取 SMTP 密码");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(credentialEncryptionKey.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("凭据加密密钥必须是 Base64", exception);
        }
        if (decoded.length != 32) throw new IllegalStateException("凭据加密密钥解码后必须为 32 字节");
        return new SecretKeySpec(decoded, "AES");
    }
}
