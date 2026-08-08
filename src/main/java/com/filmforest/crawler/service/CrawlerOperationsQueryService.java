package com.filmforest.crawler.service;

import com.filmforest.common.dto.PageResult;
import com.filmforest.common.type.ContentType;
import com.filmforest.crawler.dto.CrawlerJobFilter;
import com.filmforest.crawler.dto.CrawlerOperationsStats;
import com.filmforest.crawler.entity.CrawlerStatus;
import com.filmforest.crawler.entity.CrawlerTaskLog;
import com.filmforest.crawler.entity.CrawlerTriggerType;
import com.filmforest.crawler.mapper.CrawlerTaskLogMapper;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class CrawlerOperationsQueryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;
    private static final int MAX_KEYWORD_LENGTH = 200;

    private final CrawlerTaskLogMapper jobMapper;

    public CrawlerOperationsQueryService(CrawlerTaskLogMapper jobMapper) {
        this.jobMapper = jobMapper;
    }

    public PageResult<CrawlerTaskLog> listJobs(String status,
                                                Long scheduleId,
                                                String sourceCode,
                                                String contentType,
                                                String triggerType,
                                                OffsetDateTime from,
                                                OffsetDateTime to,
                                                String keyword,
                                                Integer page,
                                                Integer size) {
        int safePage = page == null ? 1 : Math.max(page, 1);
        int safeSize = size == null ? DEFAULT_PAGE_SIZE : Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        CrawlerJobFilter filter = new CrawlerJobFilter(
                normalizeStatus(status),
                positiveId(scheduleId),
                normalizeText(sourceCode, 50, "source"),
                normalizeContentType(contentType),
                normalizeTriggerType(triggerType),
                toUtc(from),
                toUtc(to),
                normalizeText(keyword, MAX_KEYWORD_LENGTH, "keyword")
        );
        if (filter.from() != null && filter.to() != null && !filter.from().isBefore(filter.to())) {
            throw new IllegalArgumentException("from 必须早于 to");
        }

        long total = jobMapper.countJobs(filter);
        long offset = Math.multiplyExact((long) safePage - 1, safeSize);
        List<CrawlerTaskLog> records = total == 0 || offset >= total
                ? List.of()
                : jobMapper.selectJobPage(filter, safeSize, offset);
        long pages = total == 0 ? 0 : (total + safeSize - 1) / safeSize;
        return new PageResult<>(records, total, safeSize, safePage, pages);
    }

    public CrawlerOperationsStats getOperationsStats(int days) {
        if (days != 7 && days != 30) {
            throw new IllegalArgumentException("days 仅支持 7 或 30");
        }
        LocalDate today = CrawlerTime.todayInScheduleZone();
        LocalDateTime from = CrawlerTime.startOfScheduleDayUtc(today.minusDays(days - 1L));
        LocalDateTime to = CrawlerTime.startOfScheduleDayUtc(today.plusDays(1));

        Map<String, Object> summary = jobMapper.selectOperationsSummary(from, to);
        List<CrawlerOperationsStats.Daily> daily = jobMapper.selectDailyOperations(from, to).stream()
                .map(row -> new CrawlerOperationsStats.Daily(
                        dateValue(row, "day"),
                        longValue(row, "jobs"),
                        longValue(row, "success"),
                        longValue(row, "partial"),
                        longValue(row, "failed"),
                        longValue(row, "cancelled"),
                        longValue(row, "added"),
                        longValue(row, "updated"),
                        longValue(row, "failedItems")
                ))
                .toList();
        List<CrawlerOperationsStats.SourceHealth> sourceHealth = jobMapper.selectSourceHealth(from, to).stream()
                .map(row -> new CrawlerOperationsStats.SourceHealth(
                        stringValue(row, "source", "unknown"),
                        longValue(row, "jobs"),
                        longValue(row, "success"),
                        longValue(row, "partial"),
                        longValue(row, "failed"),
                        longValue(row, "cancelled"),
                        doubleValue(row, "avgDurationMs"),
                        dateTimeValue(row, "lastRunAt")
                ))
                .toList();
        return new CrawlerOperationsStats(
                days,
                longValue(summary, "jobs"),
                longValue(summary, "success"),
                longValue(summary, "partial"),
                longValue(summary, "failed"),
                longValue(summary, "cancelled"),
                doubleValue(summary, "avgDurationMs"),
                longValue(summary, "added"),
                longValue(summary, "updated"),
                longValue(summary, "failedItems"),
                daily,
                sourceHealth
        );
    }

    private String normalizeStatus(String value) {
        String normalized = normalizeText(value, 32, "status");
        if (normalized == null || "all".equals(normalized)) {
            return null;
        }
        if (CrawlerStatus.fromCode(normalized) == null) {
            throw new IllegalArgumentException("不支持的 Job 状态: " + normalized);
        }
        return normalized;
    }

    private String normalizeContentType(String value) {
        String normalized = normalizeText(value, 20, "type");
        if (normalized == null || "all".equals(normalized)) {
            return null;
        }
        return ContentType.fromValue(normalized)
                .orElseThrow(() -> new IllegalArgumentException("不支持的内容类型: " + normalized))
                .value();
    }

    private String normalizeTriggerType(String value) {
        String normalized = normalizeText(value, 20, "triggerType");
        if (normalized == null || "all".equals(normalized)) {
            return null;
        }
        for (CrawlerTriggerType type : CrawlerTriggerType.values()) {
            if (type.getCode().equals(normalized)) {
                return normalized;
            }
        }
        throw new IllegalArgumentException("不支持的触发类型: " + normalized);
    }

    private Long positiveId(Long value) {
        if (value == null) return null;
        if (value <= 0) throw new IllegalArgumentException("scheduleId 必须为正整数");
        return value;
    }

    private String normalizeText(String value, int maxLength, String field) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " 长度不能超过 " + maxLength);
        }
        return normalized;
    }

    private LocalDateTime toUtc(OffsetDateTime value) {
        return value == null ? null : LocalDateTime.ofInstant(value.toInstant(), ZoneOffset.UTC);
    }

    private long longValue(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private double doubleValue(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value instanceof Number number ? number.doubleValue() : 0D;
    }

    private String stringValue(Map<String, Object> row, String key, String fallback) {
        Object value = value(row, key);
        return value == null ? fallback : value.toString();
    }

    private LocalDate dateValue(Map<String, Object> row, String key) {
        Object value = value(row, key);
        if (value instanceof LocalDate localDate) return localDate;
        if (value instanceof Date date) return date.toLocalDate();
        return LocalDate.parse(String.valueOf(value));
    }

    private LocalDateTime dateTimeValue(Map<String, Object> row, String key) {
        Object value = value(row, key);
        if (value == null) return null;
        if (value instanceof LocalDateTime dateTime) return dateTime;
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime();
        return LocalDateTime.parse(value.toString());
    }

    private Object value(Map<String, Object> row, String key) {
        if (row == null) return null;
        if (row.containsKey(key)) return row.get(key);
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) return entry.getValue();
        }
        return null;
    }
}
