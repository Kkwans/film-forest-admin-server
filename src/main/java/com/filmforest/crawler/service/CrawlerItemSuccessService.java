package com.filmforest.crawler.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.filmforest.common.dto.PageResult;
import com.filmforest.common.type.ContentType;
import com.filmforest.crawler.core.CrawlerContentPersistence;
import com.filmforest.crawler.entity.CrawlerJobItemSuccess;
import com.filmforest.crawler.mapper.CrawlerJobItemSuccessMapper;
import com.filmforest.crawler.model.ParsedContent;
import com.filmforest.crawler.model.SourceListItem;
import org.springframework.stereotype.Service;

import java.util.List;

/** 保存和查询按 Job 隔离的成功内容明细。 */
@Service
public class CrawlerItemSuccessService {

    private static final int SOURCE_URL_LIMIT = 1000;
    private static final int TEXT_LIMIT = 255;

    private final CrawlerJobItemSuccessMapper mapper;
    private final ObjectMapper objectMapper;
    private final CrawlerContentPersistence contentPersistence;

    public CrawlerItemSuccessService(CrawlerJobItemSuccessMapper mapper,
                                     ObjectMapper objectMapper,
                                     CrawlerContentPersistence contentPersistence) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.contentPersistence = contentPersistence;
    }

    public void record(Long jobId, String sourceCode, ContentType contentType,
                       SourceListItem item, ParsedContent parsed, List<String> resolvedGenres,
                       long contentId, String resultType) {
        CrawlerJobItemSuccess success = base(jobId, sourceCode, contentType,
                item.externalId(), item.sourceUrl(), contentId, resultType);
        fill(success, parsed, resolvedGenres);
        mapper.upsertSuccess(success);
    }

    /** 列表指纹短路时从当前内容表生成快照，避免成功明细只有内部 ID。 */
    public void recordExisting(Long jobId, String sourceCode, ContentType contentType,
                               SourceListItem item, long contentId) {
        ParsedContent snapshot = contentPersistence.snapshot(contentType, contentId,
                item.externalId(), item.sourceUrl());
        record(jobId, sourceCode, contentType, item, snapshot, snapshot.genres(),
                contentId, "UNCHANGED");
    }

    public PageResult<CrawlerJobItemSuccess> listSuccesses(Long jobId, String keyword,
                                                           Integer page, Integer size) {
        long safeJobId = requirePositive(jobId);
        int safePage = page == null ? 1 : page;
        int safeSize = size == null ? 20 : size;
        if (safePage < 1) throw new IllegalArgumentException("页码必须大于 0");
        if (safeSize < 1 || safeSize > 100) {
            throw new IllegalArgumentException("每页数量必须在 1 到 100 之间");
        }
        String safeKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        long total = mapper.countSuccesses(safeJobId, safeKeyword);
        long offset = Math.multiplyExact((long) safePage - 1, safeSize);
        List<CrawlerJobItemSuccess> records = total == 0 || offset >= total
                ? List.of()
                : mapper.selectSuccessPage(safeJobId, safeKeyword, safeSize, offset);
        long pages = total == 0 ? 0 : (total + safeSize - 1) / safeSize;
        return new PageResult<>(records, total, safeSize, safePage, pages);
    }

    private CrawlerJobItemSuccess base(Long jobId, String sourceCode, ContentType contentType,
                                       String externalId, String sourceUrl, long contentId,
                                       String resultType) {
        CrawlerJobItemSuccess success = new CrawlerJobItemSuccess();
        success.setJobId(requirePositive(jobId));
        success.setSourceCode(limit(requireText(sourceCode, "sourceCode"), 50));
        success.setContentType(contentType.value());
        success.setExternalId(limit(requireText(externalId, "externalId"), 100));
        success.setSourceUrl(limit(requireText(sourceUrl, "sourceUrl"), SOURCE_URL_LIMIT));
        success.setContentId(contentId);
        success.setResultType(resultType);
        success.setCrawledAt(CrawlerTime.nowUtc());
        return success;
    }

    private void fill(CrawlerJobItemSuccess success, ParsedContent parsed,
                      List<String> resolvedGenres) {
        success.setTitle(limit(requireText(parsed.title(), "title"), TEXT_LIMIT));
        success.setAlias(json(parsed.aliases()));
        success.setPosterUrl(limit(parsed.sourcePosterUrl(), 1000));
        success.setYear(parsed.year());
        success.setDirectors(json(parsed.directors()));
        success.setWriters(json(parsed.writers()));
        success.setActors(json(parsed.actors()));
        success.setGenres(json(resolvedGenres == null ? parsed.genres() : resolvedGenres));
        success.setRegions(json(parsed.regions()));
        success.setLanguages(json(parsed.languages()));
        success.setReleaseDate(limit(parsed.rawReleaseDate(), 100));
        success.setDuration(parsed.durationMinutes());
        success.setTotalEpisodes(parsed.totalEpisodes());
        success.setScoreDouban(parsed.doubanScore());
        success.setScoreImdb(parsed.imdbScore());
        success.setScoreRt(parsed.rottenTomatoesScore());
    }

    private String json(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("无法保存成功明细多值字段", error);
        }
    }

    private static Long requirePositive(Long value) {
        if (value == null || value <= 0) throw new IllegalArgumentException("jobId must be positive");
        return value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    private static String limit(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
