package com.filmforest.common.auth;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AuthControllerBoundaryTest {

    @Test
    void doesNotExposePublicRegistrationHandler() {
        boolean hasRegisterHandler = Arrays.stream(AuthController.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(PostMapping.class))
                .filter(mapping -> mapping != null)
                .flatMap(mapping -> Arrays.stream(mapping.value()))
                .anyMatch("/register"::equals);

        assertThat(hasRegisterHandler).isFalse();
    }
}
