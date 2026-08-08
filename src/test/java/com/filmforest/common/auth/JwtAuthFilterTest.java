package com.filmforest.common.auth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class JwtAuthFilterTest {

    private final JwtAuthFilter filter = new JwtAuthFilter(mock(JwtUtil.class));

    @Test
    void allowsOnlyExplicitPublicEndpoints() {
        assertThat(isPublic("POST", "/api/auth/login")).isTrue();
        assertThat(isPublic("GET", "/api/health")).isTrue();
        assertThat(isPublic("OPTIONS", "/api/settings")).isTrue();
    }

    @Test
    void protectsRegistrationAndAllOtherAdminEndpoints() {
        assertThat(isPublic("GET", "/api/auth/login")).isFalse();
        assertThat(isPublic("POST", "/api/auth/register")).isFalse();
        assertThat(isPublic("GET", "/api/settings")).isFalse();
        assertThat(isPublic("POST", "/api/crawler/start")).isFalse();
    }

    private boolean isPublic(String method, String path) {
        return filter.isPublicRequest(new MockHttpServletRequest(method, path));
    }
}
