package com.filmforest.content.service;

import com.filmforest.common.dto.PageResult;
import com.filmforest.content.dto.AdminContentItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminContentQueryServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void queriesAllTypesWithOneRealCountAndGlobalPage() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(101L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());

        PageResult<AdminContentItem> result = new AdminContentQueryService(jdbcTemplate)
                .search(null, null, null, "createdAt", "desc", 2, 20);

        assertThat(result.total()).isEqualTo(101);
        assertThat(result.current()).isEqualTo(2);
        assertThat(result.pages()).isEqualTo(6);

        ArgumentCaptor<String> dataSql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> dataArguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(dataSql.capture(), any(RowMapper.class), dataArguments.capture());
        assertThat(dataSql.getValue()).contains("UNION ALL", "FROM movie", "FROM short_drama",
                "ORDER BY created_at DESC", "LIMIT ? OFFSET ?");
        assertThat(dataArguments.getValue()).containsExactly(20, 20L);
    }

    @Test
    void appliesTypeStatusAndKeywordBeforeCounting() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(0L);

        PageResult<AdminContentItem> result = new AdminContentQueryService(jdbcTemplate)
                .search("movie", 1, "森林", "title", "asc", 1, 20);

        assertThat(result.records()).isEmpty();
        ArgumentCaptor<String> countSql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> countArguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForObject(countSql.capture(), eq(Long.class), countArguments.capture());
        assertThat(countSql.getValue()).contains("FROM movie", "status = ?", "title LIKE ?");
        assertThat(countSql.getValue()).doesNotContain("UNION ALL", "FROM drama");
        assertThat(countArguments.getValue()).containsExactly(1, "%森林%", "%森林%");
    }

    @Test
    void rejectsUnknownTypesAndStatusesBeforeSql() {
        AdminContentQueryService service = new AdminContentQueryService(jdbcTemplate);

        assertThatThrownBy(() -> service.search("movie; DROP TABLE user", null, null,
                null, null, 1, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.search("movie", 3, null,
                null, null, 1, 20)).isInstanceOf(IllegalArgumentException.class);
    }
}
