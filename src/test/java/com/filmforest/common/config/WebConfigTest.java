package com.filmforest.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WebConfigTest {

    @Test
    @SuppressWarnings("unchecked")
    void contentStatusPatchIsAllowedByCors() {
        CorsRegistry registry = new CorsRegistry();
        new WebConfig(new CorsProperties(List.of("http://localhost:3001")))
                .addCorsMappings(registry);

        Map<String, CorsConfiguration> configurations =
                (Map<String, CorsConfiguration>) ReflectionTestUtils.invokeMethod(
                        registry, "getCorsConfigurations");

        assertThat(configurations).containsKey("/api/**");
        assertThat(configurations.get("/api/**").getAllowedMethods())
                .contains("PATCH");
    }
}
