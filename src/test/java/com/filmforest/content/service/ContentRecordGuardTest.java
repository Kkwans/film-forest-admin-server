package com.filmforest.content.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentRecordGuardTest {

    @Mock private JdbcTemplate jdbcTemplate;

    @Test
    void shortRouteAliasUsesCanonicalTable() {
        when(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM short_drama WHERE id = ? AND is_deleted = 0",
                Integer.class,
                7L)).thenReturn(1);

        new ContentRecordGuard(jdbcTemplate).requireActiveRecord("short", 7L);

        verify(jdbcTemplate).queryForObject(
                "SELECT COUNT(*) FROM short_drama WHERE id = ? AND is_deleted = 0",
                Integer.class,
                7L);
    }

    @Test
    void missingOrDeletedContentIsRejectedBeforeRelationWrite() {
        when(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM movie WHERE id = ? AND is_deleted = 0",
                Integer.class,
                99L)).thenReturn(0);

        assertThatThrownBy(() -> new ContentRecordGuard(jdbcTemplate)
                .requireActiveRecord("movie", 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不存在或已删除");
    }

    @Test
    void invalidPolymorphicTargetIsRejectedWithoutSql() {
        ContentRecordGuard guard = new ContentRecordGuard(jdbcTemplate);

        assertThatThrownBy(() -> guard.requireActiveRecord("../../user", 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持");
        assertThatThrownBy(() -> guard.requireActiveRecord("movie", 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("正整数");
    }
}
