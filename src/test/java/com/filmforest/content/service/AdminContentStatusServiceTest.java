package com.filmforest.content.service;

import com.filmforest.common.exception.BusinessException;
import com.filmforest.content.dto.ContentStatusBatchResult;
import com.filmforest.content.dto.ContentStatusTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminContentStatusServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;
    private AdminContentStatusService service;

    @BeforeEach
    void setUp() {
        service = new AdminContentStatusService(jdbcTemplate);
    }

    @Test
    void validatesEveryTargetThenUpdatesGroupedTables() {
        when(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM movie WHERE is_deleted = 0 AND id IN (?,?)",
                Long.class, 3L, 8L)).thenReturn(2L);
        when(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM short_drama WHERE is_deleted = 0 AND id IN (?)",
                Long.class, 5L)).thenReturn(1L);
        when(jdbcTemplate.update(
                "UPDATE movie SET status = ?, updated_at = NOW() WHERE is_deleted = 0 AND id IN (?,?)",
                2, 3L, 8L)).thenReturn(2);
        when(jdbcTemplate.update(
                "UPDATE short_drama SET status = ?, updated_at = NOW() WHERE is_deleted = 0 AND id IN (?)",
                2, 5L)).thenReturn(1);

        ContentStatusBatchResult result = service.updateStatuses(List.of(
                new ContentStatusTarget("movie", 3L),
                new ContentStatusTarget("movie", 8L),
                new ContentStatusTarget("short", 5L)
        ), 2);

        assertThat(result).isEqualTo(new ContentStatusBatchResult(3, 3, 2));
    }

    @Test
    void rejectsWholeBatchBeforeWritingWhenAnyTargetIsMissing() {
        when(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM movie WHERE is_deleted = 0 AND id IN (?,?)",
                Long.class, 3L, 99L)).thenReturn(1L);

        assertThatThrownBy(() -> service.updateStatuses(List.of(
                new ContentStatusTarget("movie", 3L),
                new ContentStatusTarget("movie", 99L)
        ), 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在或已删除");

        verify(jdbcTemplate, never()).update(
                "UPDATE movie SET status = ?, updated_at = NOW() WHERE is_deleted = 0 AND id IN (?,?)",
                1, 3L, 99L);
    }

    @Test
    void rejectsDuplicateCanonicalTargets() {
        assertThatThrownBy(() -> service.updateStatuses(List.of(
                new ContentStatusTarget("short", 7L),
                new ContentStatusTarget("short_drama", 7L)
        ), 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重复内容");
    }
}
