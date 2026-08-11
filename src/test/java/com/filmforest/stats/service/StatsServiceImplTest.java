package com.filmforest.stats.service;

import com.filmforest.common.type.ContentType;
import com.filmforest.stats.service.impl.StatsServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatsServiceImplTest {

    @Test
    void emptyAggregatesRemainCompleteZeroValuedReport() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(0L);

        Map<String, Object> crawlerRow = new HashMap<>();
        crawlerRow.put("total_runs", 0L);
        crawlerRow.put("success_runs", null);
        crawlerRow.put("failed_runs", null);
        crawlerRow.put("total_items", 0L);
        crawlerRow.put("total_added", 0L);
        crawlerRow.put("total_updated", 0L);
        crawlerRow.put("avg_duration", null);
        when(jdbcTemplate.queryForMap(contains("FROM crawler_task_log"), nullable(Object[].class)))
                .thenReturn(crawlerRow);

        Map<String, Object> qualityRow = new HashMap<>();
        qualityRow.put("total", 0L);
        qualityRow.put("high_score", null);
        qualityRow.put("mid_score", null);
        qualityRow.put("low_score", null);
        qualityRow.put("avg_score", null);
        when(jdbcTemplate.queryForMap(contains("score_douban"), nullable(Object[].class)))
                .thenReturn(qualityRow);

        StatsServiceImpl service = new StatsServiceImpl();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);

        Map<String, Object> report = service.getReport(1);

        assertThat(report.get("crawlerEfficiency")).isEqualTo(Map.of(
                "totalRuns", 0L,
                "terminalRuns", 0L,
                "successRuns", 0L,
                "failedRuns", 0L,
                "totalItems", 0L,
                "totalAdded", 0L,
                "totalUpdated", 0L,
                "avgDurationMs", 0L,
                "successRate", 0.0
        ));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> qualityStats =
                (List<Map<String, Object>>) report.get("qualityStats");
        assertThat(qualityStats).hasSize(5).allSatisfy(item -> {
            assertThat(item).containsEntry("total", 0L)
                    .containsEntry("highScore", 0L)
                    .containsEntry("midScore", 0L)
                    .containsEntry("lowScore", 0L)
                    .containsEntry("avgScore", 0.0);
        });
    }

    @Test
    void overviewCountsOnlyAvailableResourcesAndUsesTerminalSuccessRate() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("resource_online")) return 4;
            if (sql.contains("resource_magnet")) return 7;
            if (sql.contains("resource_cloud")) return 3;
            return 0;
        });
        when(jdbcTemplate.queryForMap(contains("terminal_runs"))).thenReturn(Map.of(
                "total_runs", 6L,
                "success_runs", 2L,
                "failed_runs", 1L,
                "terminal_runs", 4L,
                "total_items", 11L
        ));
        StatsServiceImpl service = new StatsServiceImpl();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);

        Map<String, Object> overview = service.getOverview();

        @SuppressWarnings("unchecked")
        Map<String, Object> crawler = (Map<String, Object>) overview.get("crawler");
        assertThat(crawler).containsEntry("totalRuns", 6L)
                .containsEntry("successRuns", 2L)
                .containsEntry("successRate", 50.0)
                .containsEntry("totalItemsCrawled", 11L);
        @SuppressWarnings("unchecked")
        Map<String, Object> resources = (Map<String, Object>) overview.get("resources");
        assertThat(resources).containsEntry("online", 4)
                .containsEntry("magnet", 7)
                .containsEntry("cloud", 3)
                .containsEntry("total", 14);
        verify(jdbcTemplate).queryForObject(
                "SELECT COUNT(*) FROM resource_online WHERE is_deleted = 0 AND enabled = 1 AND removed_at IS NULL",
                Integer.class);
    }

    @Test
    void contentExportKeepsDraftPublishedAndOfflineDistinct() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of());
        StatsServiceImpl service = new StatsServiceImpl();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);

        service.getContentList(ContentType.MOVIE);

        verify(jdbcTemplate).queryForList(contains(
                "CASE status WHEN 0 THEN '草稿' WHEN 1 THEN '已发布' WHEN 2 THEN '已下线'"));
    }
}
