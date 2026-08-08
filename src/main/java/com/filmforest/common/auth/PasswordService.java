package com.filmforest.common.auth;

import cn.hutool.crypto.digest.BCrypt;
import com.filmforest.content.entity.PasswordAlgorithm;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class PasswordService {

    static final int BCRYPT_COST = 12;

    public String encode(String rawPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("密码不能为空");
        }
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt(BCRYPT_COST));
    }

    public Verification verify(String rawPassword, String storedHash, PasswordAlgorithm algorithm) {
        if (rawPassword == null || storedHash == null || storedHash.isBlank()) {
            return Verification.invalid();
        }

        if (looksLikeBcrypt(storedHash)) {
            try {
                boolean matches = BCrypt.checkpw(rawPassword, storedHash);
                boolean needsUpgrade = matches
                        && (algorithm != PasswordAlgorithm.BCRYPT || bcryptCost(storedHash) < BCRYPT_COST);
                return new Verification(matches, needsUpgrade);
            } catch (IllegalArgumentException ignored) {
                return Verification.invalid();
            }
        }

        if (looksLikeLegacySha256(storedHash)) {
            boolean matches = MessageDigest.isEqual(
                    HexFormat.of().parseHex(storedHash),
                    sha256(rawPassword));
            return new Verification(matches, matches);
        }

        return Verification.invalid();
    }

    private boolean looksLikeBcrypt(String hash) {
        return hash.length() == 60
                && (hash.startsWith("$2a$") || hash.startsWith("$2b$") || hash.startsWith("$2y$"));
    }

    private boolean looksLikeLegacySha256(String hash) {
        if (hash.length() != 64) {
            return false;
        }
        for (int i = 0; i < hash.length(); i++) {
            if (Character.digit(hash.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    private int bcryptCost(String hash) {
        try {
            return Integer.parseInt(hash.substring(4, 6));
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private byte[] sha256(String rawPassword) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(rawPassword.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 不支持 SHA-256", e);
        }
    }

    public record Verification(boolean matches, boolean needsUpgrade) {
        static Verification invalid() {
            return new Verification(false, false);
        }
    }
}
