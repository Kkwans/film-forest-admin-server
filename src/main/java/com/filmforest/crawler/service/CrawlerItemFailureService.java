package com.filmforest.crawler.service;

import com.filmforest.common.dto.PageResult;
import com.filmforest.common.type.ContentType;
import com.filmforest.crawler.entity.CrawlerFailureStage;
import com.filmforest.crawler.entity.CrawlerJobItemFailure;
import com.filmforest.crawler.mapper.CrawlerJobItemFailureMapper;
import com.filmforest.crawler.model.SourceListItem;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class CrawlerItemFailureService {

    private static final int SOURCE_URL_LIMIT = 1000;
    private static final int ERROR_CATEGORY_LIMIT = 64;
    private static final int DIAGNOSTIC_LIMIT = 1000;

    private final CrawlerJobItemFailureMapper mapper;

    public CrawlerItemFailureService(CrawlerJobItemFailureMapper mapper) {
        this.mapper = mapper;
    }

    public void record(Long jobId, String sourceCode, ContentType contentType,
                       SourceListItem item, CrawlerFailureStage stage,
                       String errorCategory, int attempts, boolean retryExhausted,
                       String diagnostic) {
        CrawlerJobItemFailure failure = new CrawlerJobItemFailure();
        failure.setJobId(requirePositive(jobId));
        failure.setSourceCode(limit(requireText(sourceCode, "sourceCode"), 50));
        failure.setContentType(contentType.value());
        failure.setExternalId(limit(requireText(item.externalId(), "externalId"), 100));
        failure.setSourceUrl(limit(requireText(item.sourceUrl(), "sourceUrl"), SOURCE_URL_LIMIT));
        failure.setFailureStage(stage.getCode());
        failure.setErrorCategory(limit(requireText(errorCategory, "errorCategory"),
                ERROR_CATEGORY_LIMIT));
        failure.setAttemptCount(Math.max(1, attempts));
        failure.setRetryExhausted(retryExhausted);
        failure.setDiagnostic(limit(normalizeDiagnostic(diagnostic), DIAGNOSTIC_LIMIT));
        failure.setFailedAt(CrawlerTime.nowUtc());
        mapper.upsertFailure(failure);
    }

    /**
     * 查询单个 Job 的条目失败，分页参数由服务端严格约束。
     */
    public PageResult<CrawlerJobItemFailure> listFailures(Long jobId,
                                                          String stage,
                                                          String category,
                                                          Boolean retryExhausted,
                                                          Integer page,
                                                          Integer size) {
        Long safeJobId = requirePositive(jobId);
        String safeStage = normalizeStage(stage);
        String safeCategory = normalizeCategory(category);
        int safePage = page == null ? 1 : page;
        int safeSize = size == null ? 20 : size;
        if (safePage < 1) {
            throw new IllegalArgumentException("页码必须大于 0");
        }
        if (safeSize < 1 || safeSize > 100) {
            throw new IllegalArgumentException("每页数量必须在 1 到 100 之间");
        }

        long total = mapper.countFailures(safeJobId, safeStage, safeCategory, retryExhausted);
        long offset = Math.multiplyExact((long) safePage - 1, safeSize);
        List<CrawlerJobItemFailure> records = total == 0 || offset >= total
                ? List.of()
                : mapper.selectFailurePage(safeJobId, safeStage, safeCategory,
                retryExhausted, safeSize, offset);
        long pages = total == 0 ? 0 : (total + safeSize - 1) / safeSize;
        return new PageResult<>(records, total, safeSize, safePage, pages);
    }

    private static Long requirePositive(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("jobId must be positive");
        }
        return value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String normalizeDiagnostic(String value) {
        return value == null || value.isBlank() ? null : value.trim().replaceAll("\\s+", " ");
    }

    private static String normalizeStage(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (CrawlerFailureStage stage : CrawlerFailureStage.values()) {
            if (stage.getCode().equals(normalized)) {
                return normalized;
            }
        }
        throw new IllegalArgumentException("不支持的失败阶段: " + value.trim());
    }

    private static String normalizeCategory(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > ERROR_CATEGORY_LIMIT) {
            throw new IllegalArgumentException("category 长度不能超过 " + ERROR_CATEGORY_LIMIT);
        }
        return normalized;
    }

    private static String limit(String value, int maxLength) {
        return value == null || value.length() <= maxLength
                ? value : value.substring(0, maxLength);
    }
}
