package com.filmforest.common.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityPropertiesTest {

    @Test
    void corsOriginsAreTrimmedAndDeduplicated() {
        CorsProperties properties = new CorsProperties(List.of(
                " http://localhost:3001 ",
                "http://localhost:3001",
                "https://admin.example.test"
        ));

        assertThat(properties.allowedOrigins())
                .containsExactly("http://localhost:3001", "https://admin.example.test");
    }

    @Test
    void wildcardCorsOriginIsRejected() {
        assertThatThrownBy(() -> new CorsProperties(List.of("*")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shortJwtSecretIsRejected() {
        assertThatThrownBy(() -> new JwtProperties("too-short", 60_000, "film-forest-admin"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
