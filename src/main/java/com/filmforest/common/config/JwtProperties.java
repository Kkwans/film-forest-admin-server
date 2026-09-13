package com.filmforest.common.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        @NotBlank String secret,
        @Positive long expiration,
        @NotBlank String issuer,
        @Positive long rememberedExpiration
) {
    public JwtProperties(String secret, long expiration, String issuer) {
        this(secret, expiration, issuer, Math.max(expiration, 2_592_000_000L));
    }

    @ConstructorBinding
    public JwtProperties {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("JWT 密钥至少需要 32 个字符");
        }
        if (rememberedExpiration < expiration) {
            throw new IllegalArgumentException("记住登录状态的 Token 有效期不能短于普通登录");
        }
    }
}
