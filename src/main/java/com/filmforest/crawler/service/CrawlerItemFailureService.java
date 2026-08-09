package com.filmforest.crawler.service;

import com.filmforest.common.type.ContentType;
import com.filmforest.crawler.entity.CrawlerFailureStage;
import com.filmforest.crawler.entity.CrawlerJobItemFailure;
import com.filmforest.crawler.mapper.CrawlerJobItemFailureMapper;
import com.filmforest.crawler.model.SourceListItem;
import org.springframework.stereotype.Service;

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

    private static String limit(String value, int maxLength) {
        return value == null || value.length() <= maxLength
                ? value : value.substring(0, maxLength);
    }
}
