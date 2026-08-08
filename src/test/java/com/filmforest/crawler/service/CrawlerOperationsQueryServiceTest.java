package com.filmforest.crawler.service;

import com.filmforest.crawler.dto.CrawlerJobFilter;
import com.filmforest.crawler.entity.CrawlerTaskLog;
import com.filmforest.crawler.mapper.CrawlerTaskLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("爬虫运行查询服务")
class CrawlerOperationsQueryServiceTest {

    @Mock private CrawlerTaskLogMapper jobMapper;

    private CrawlerOperationsQueryService service;

    @BeforeEach
    void setUp() {
        service = new CrawlerOperationsQueryService(jobMapper);
    }

    @Test
    @DisplayName("日志查询返回真实总数并绑定全部筛选条件")
    void listJobs_shouldReturnRealPageAndBoundFilters() {
        CrawlerTaskLog job = new CrawlerTaskLog();
        job.setId(21L);
        when(jobMapper.countJobs(any())).thenReturn(41L);
        when(jobMapper.selectJobPage(any(), eq(20), eq(20L))).thenReturn(List.of(job));

        var result = service.listJobs(
                "failed", 3L, "PKMP4", "movie", "manual",
                OffsetDateTime.parse("2026-08-01T00:00:00+08:00"),
                OffsetDateTime.parse("2026-08-09T00:00:00+08:00"),
                "  Connection Timeout  ", 2, 20);

        assertThat(result.total()).isEqualTo(41);
        assertThat(result.current()).isEqualTo(2);
        assertThat(result.pages()).isEqualTo(3);
        assertThat(result.records()).containsExactly(job);

        ArgumentCaptor<CrawlerJobFilter> filterCaptor = ArgumentCaptor.forClass(CrawlerJobFilter.class);
        verify(jobMapper).countJobs(filterCaptor.capture());
        CrawlerJobFilter filter = filterCaptor.getValue();
        assertThat(filter.status()).isEqualTo("failed");
        assertThat(filter.scheduleId()).isEqualTo(3L);
        assertThat(filter.sourceCode()).isEqualTo("pkmp4");
        assertThat(filter.contentType()).isEqualTo("movie");
        assertThat(filter.triggerType()).isEqualTo("manual");
        assertThat(filter.keyword()).isEqualTo("connection timeout");
        assertThat(filter.from()).isEqualTo(LocalDateTime.parse("2026-07-31T16:00:00"));
        assertThat(filter.to()).isEqualTo(LocalDateTime.parse("2026-08-08T16:00:00"));
    }

    @Test
    @DisplayName("非法枚举和倒置时间范围在访问数据库前拒绝")
    void listJobs_shouldRejectInvalidFilters() {
        assertThatThrownBy(() -> service.listJobs(
                "finished", null, null, null, null, null, null, null, 1, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("状态");
        assertThatThrownBy(() -> service.listJobs(
                null, null, null, "series", null, null, null, null, 1, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("内容类型");
        assertThatThrownBy(() -> service.listJobs(
                null, null, null, null, null,
                OffsetDateTime.parse("2026-08-09T00:00:00Z"),
                OffsetDateTime.parse("2026-08-08T00:00:00Z"), null, 1, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("from");
    }

    @Test
    @DisplayName("7 天统计映射 SQL 聚合结果和来源健康度")
    void getOperationsStats_shouldMapSqlAggregates() {
        when(jobMapper.selectOperationsSummary(any(), any())).thenReturn(Map.of(
                "jobs", 10L,
                "success", 7L,
                "partial", 1L,
                "failed", 1L,
                "cancelled", 1L,
                "avgDurationMs", new BigDecimal("1250.5"),
                "added", 30L,
                "updated", 12L,
                "failedItems", 2L));
        when(jobMapper.selectDailyOperations(any(), any())).thenReturn(List.of(Map.of(
                "day", Date.valueOf(LocalDate.of(2026, 8, 9)),
                "jobs", 2L,
                "success", 1L,
                "partial", 1L,
                "failed", 0L,
                "cancelled", 0L,
                "added", 5L,
                "updated", 3L,
                "failedItems", 1L)));
        when(jobMapper.selectSourceHealth(any(), any())).thenReturn(List.of(Map.of(
                "source", "pkmp4",
                "jobs", 10L,
                "success", 7L,
                "partial", 1L,
                "failed", 1L,
                "cancelled", 1L,
                "avgDurationMs", new BigDecimal("1250.5"),
                "lastRunAt", Timestamp.valueOf(LocalDateTime.of(2026, 8, 9, 2, 0)))));

        var result = service.getOperationsStats(7);

        assertThat(result.days()).isEqualTo(7);
        assertThat(result.jobs()).isEqualTo(10);
        assertThat(result.avgDurationMs()).isEqualTo(1250.5);
        assertThat(result.daily()).singleElement().satisfies(day -> {
            assertThat(day.date()).isEqualTo(LocalDate.of(2026, 8, 9));
            assertThat(day.failedItems()).isEqualTo(1);
        });
        assertThat(result.sourceHealth()).singleElement().satisfies(source -> {
            assertThat(source.source()).isEqualTo("pkmp4");
            assertThat(source.lastRunAt()).isEqualTo(LocalDateTime.of(2026, 8, 9, 2, 0));
        });
    }

    @Test
    @DisplayName("统计窗口只允许 7 或 30 天")
    void getOperationsStats_shouldRejectUnsupportedWindow() {
        assertThatThrownBy(() -> service.getOperationsStats(14))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("7 或 30");
    }
}
