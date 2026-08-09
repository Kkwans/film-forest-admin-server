package com.filmforest.content.controller;

import com.filmforest.common.dto.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContentControllerStatusTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final ContentController controller = new ContentController();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "jdbcTemplate", jdbcTemplate);
    }

    @Test
    void acceptsCanonicalShortDramaTypeForThreeStateUpdates() {
        when(jdbcTemplate.update(
                "UPDATE short_drama SET status = ?, updated_at = NOW() WHERE id = ?", 2, 17L))
                .thenReturn(1);

        Result<Boolean> result = controller.toggleStatus("short_drama", 17L, 2);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isTrue();
        verify(jdbcTemplate).update(
                "UPDATE short_drama SET status = ?, updated_at = NOW() WHERE id = ?", 2, 17L);
    }

    @Test
    void rejectsStatusesOutsideDraftPublishedAndOffline() {
        Result<Boolean> result = controller.toggleStatus("movie", 1L, 3);

        assertThat(result.getCode()).isNotEqualTo(200);
        assertThat(result.getMessage()).contains("0、1 或 2");
    }
}
