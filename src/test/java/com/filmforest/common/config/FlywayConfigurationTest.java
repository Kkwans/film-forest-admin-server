package com.filmforest.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.env.PropertySource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayConfigurationTest {

    @Test
    void migrationRequiresExplicitOptIn() throws IOException {
        List<PropertySource<?>> propertySources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));

        assertThat(propertySources)
                .extracting(source -> source.getProperty("spring.flyway.enabled"))
                .containsExactly("${FILM_FOREST_FLYWAY_ENABLED:false}");
    }
}
