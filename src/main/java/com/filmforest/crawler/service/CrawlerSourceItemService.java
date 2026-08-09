package com.filmforest.crawler.service;

import com.filmforest.common.type.ContentType;
import com.filmforest.crawler.entity.CrawlerSourceItem;
import com.filmforest.crawler.mapper.CrawlerSourceItemMapper;
import com.filmforest.crawler.model.SourceListItem;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class CrawlerSourceItemService {

    private final CrawlerSourceItemMapper mapper;

    public CrawlerSourceItemService(CrawlerSourceItemMapper mapper) {
        this.mapper = mapper;
    }

    public Observation observeListItem(String sourceCode, ContentType contentType,
                                       SourceListItem item) {
        String normalizedSource = requireText(sourceCode, "sourceCode");
        String externalId = requireText(item.externalId(), "externalId");
        String listFingerprint = SourceFingerprint.forListItem(item);
        CrawlerSourceItem previous = mapper.selectBySourceKey(
                normalizedSource, contentType.value(), externalId);
        LocalDateTime now = CrawlerTime.nowUtc();
        mapper.upsertListObservation(normalizedSource, contentType.value(), externalId,
                requireText(item.sourceUrl(), "sourceUrl"), listFingerprint, now);
        CrawlerSourceItem current = mapper.selectBySourceKey(
                normalizedSource, contentType.value(), externalId);
        return new Observation(
                current == null ? null : current.getId(),
                previous != null,
                previous == null || !Objects.equals(previous.getListFingerprint(), listFingerprint),
                previous == null ? null : previous.getDetailFingerprint(),
                previous == null ? null : previous.getInternalContentId(),
                previous == null ? null : previous.getLastParseStatus());
    }

    public void recordParsed(String sourceCode, ContentType contentType, String externalId,
                             long internalContentId, String canonicalKey, String detailFingerprint) {
        record(sourceCode, contentType, externalId, internalContentId,
                requireText(canonicalKey, "canonicalKey"),
                requireText(detailFingerprint, "detailFingerprint"), "parsed", null, true);
    }

    public void recordFiltered(String sourceCode, ContentType contentType, String externalId,
                               String detailFingerprint) {
        record(sourceCode, contentType, externalId, null, null,
                requireText(detailFingerprint, "detailFingerprint"), "filtered", null, true);
    }

    public void recordFetchFailure(String sourceCode, ContentType contentType, String externalId,
                                   String errorCategory) {
        record(sourceCode, contentType, externalId, null, null, null,
                "fetch_failed", requireText(errorCategory, "errorCategory"), true);
    }

    public void recordParseFailure(String sourceCode, ContentType contentType, String externalId,
                                   String errorCategory) {
        record(sourceCode, contentType, externalId, null, null, null,
                "parse_failed", requireText(errorCategory, "errorCategory"), true);
    }

    public void recordPersistFailure(String sourceCode, ContentType contentType, String externalId,
                                     String detailFingerprint, String errorCategory) {
        record(sourceCode, contentType, externalId, null, null, detailFingerprint,
                "persist_failed", requireText(errorCategory, "errorCategory"), true);
    }

    private void record(String sourceCode, ContentType contentType, String externalId,
                        Long internalContentId, String canonicalKey, String detailFingerprint, String status,
                        String errorCategory, boolean fetched) {
        mapper.recordOutcome(requireText(sourceCode, "sourceCode"), contentType.value(),
                requireText(externalId, "externalId"), internalContentId, canonicalKey,
                detailFingerprint, fetched ? CrawlerTime.nowUtc() : null, status,
                limit(errorCategory, 64));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String limit(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public record Observation(Long id, boolean knownBefore, boolean listChanged,
                              String previousDetailFingerprint, Long internalContentId,
                              String previousParseStatus) {
    }
}
