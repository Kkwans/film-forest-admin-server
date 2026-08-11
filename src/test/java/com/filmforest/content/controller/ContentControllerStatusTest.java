package com.filmforest.content.controller;

import com.filmforest.common.dto.Result;
import com.filmforest.content.service.AdminContentStatusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContentControllerStatusTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final ContentController controller = new ContentController();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "adminContentStatusService",
                new AdminContentStatusService(jdbcTemplate));
    }

    @Test
    void acceptsCanonicalShortDramaTypeForThreeStateUpdates() {
        when(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM short_drama WHERE is_deleted = 0 AND id IN (?)",
                Long.class, 17L)).thenReturn(1L);
        when(jdbcTemplate.update(
                "UPDATE short_drama SET status = ?, updated_at = NOW() WHERE is_deleted = 0 AND id IN (?)",
                2, 17L))
                .thenReturn(1);

        Result<Boolean> result = controller.toggleStatus("short_drama", 17L, 2);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isTrue();
        verify(jdbcTemplate).update(
                "UPDATE short_drama SET status = ?, updated_at = NOW() WHERE is_deleted = 0 AND id IN (?)",
                2, 17L);
    }

    @Test
    void rejectsStatusesOutsideDraftPublishedAndOffline() {
        assertThatThrownBy(() -> controller.toggleStatus("movie", 1L, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0、1 或 2");
    }
}
