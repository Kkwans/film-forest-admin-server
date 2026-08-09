package com.filmforest.system.controller;

import com.filmforest.common.dto.Result;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HealthControllerTest {

    private final HealthController controller = new HealthController();

    @Test
    void returnsStableServiceIdentity() {
        Result<Map<String, Object>> result = controller.health();

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData())
                .containsEntry("status", "ok")
                .containsEntry("service", "film-forest-admin")
                .containsKey("timestamp");
    }
}
