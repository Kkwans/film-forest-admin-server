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
    void migrationRequiresExplicitOptInAndUsesDedicatedDataSource() throws IOException {
        List<PropertySource<?>> propertySources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));

        assertThat(propertySources)
                .extracting(source -> source.getProperty("spring.flyway.enabled"))
                .containsExactly("${FILM_FOREST_FLYWAY_ENABLED:false}");
        assertThat(propertySources)
                .extracting(source -> source.getProperty("spring.flyway.url"))
                .containsExactly("${FILM_FOREST_DB_URL}");
        assertThat(propertySources)
                .extracting(source -> source.getProperty("spring.flyway.user"))
                .containsExactly("${FILM_FOREST_DB_USERNAME}");
        assertThat(propertySources)
                .extracting(source -> source.getProperty("spring.flyway.password"))
                .containsExactly("${FILM_FOREST_DB_PASSWORD}");
    }
}
