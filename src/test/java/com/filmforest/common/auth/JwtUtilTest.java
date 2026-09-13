package com.filmforest.common.auth;

import com.filmforest.common.config.JwtProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void validatesSignatureIssuerAndClaims() {
        JwtUtil jwtUtil = new JwtUtil(new JwtProperties(SECRET, 60_000, "film-forest-admin"));

        String token = jwtUtil.generateToken(7L, "admin");

        assertThat(jwtUtil.validateToken(token)).isTrue();
        assertThat(jwtUtil.getUserId(token)).isEqualTo(7L);
        assertThat(jwtUtil.getUsername(token)).isEqualTo("admin");
        assertThat(new JwtUtil(new JwtProperties(SECRET, 60_000, "another-issuer"))
                .validateToken(token)).isFalse();
    }

    @Test
    void rememberedTokenUsesTheLongerConfiguredLifetime() {
        JwtProperties properties = new JwtProperties(SECRET, 60_000, "film-forest-admin", 300_000);
        JwtUtil jwtUtil = new JwtUtil(properties);

        String normal = jwtUtil.generateToken(7L, "admin");
        String remembered = jwtUtil.generateToken(7L, "admin", true);

        assertThat(jwtUtil.parseToken(remembered).getExpiration())
                .isAfter(jwtUtil.parseToken(normal).getExpiration());
    }
}
