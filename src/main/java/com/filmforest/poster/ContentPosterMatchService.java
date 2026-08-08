package com.filmforest.poster;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.filmforest.common.type.ContentType;
import com.filmforest.content.entity.ContentPosterMatch;
import com.filmforest.content.mapper.ContentPosterMatchMapper;
import com.filmforest.crawler.service.CrawlerTime;
import com.filmforest.poster.tmdb.TmdbPosterMatchResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;

@Service
public class ContentPosterMatchService {

    private final ContentPosterMatchMapper mapper;
    private final ObjectMapper objectMapper;

    public ContentPosterMatchService(ContentPosterMatchMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ContentPosterMatch save(ContentType contentType, long contentId, String sourcePosterUrl,
                                   TmdbPosterMatchResult result) {
        ContentPosterMatch existing = find(contentType, contentId);
        if (existing != null && "accepted".equals(existing.getMatchStatus())
                && result.status() != TmdbPosterMatchResult.Status.ACCEPTED) {
            return existing;
        }

        ContentPosterMatch entity = existing == null ? new ContentPosterMatch() : existing;
        entity.setContentType(contentType.value());
        entity.setContentId(contentId);
        if (sourcePosterUrl != null && !sourcePosterUrl.isBlank()) entity.setSourcePosterUrl(sourcePosterUrl);
        if (result.candidate() != null) {
            entity.setTmdbMediaType(result.candidate().mediaType().apiValue());
            entity.setTmdbId(result.candidate().id());
        }
        if (result.poster() != null) {
            entity.setPosterPath(result.poster().filePath());
            entity.setPosterLanguage(result.poster().language());
        }
        entity.setConfidence(scale(result.confidence()));
        entity.setMatchStatus(result.status().name().toLowerCase(Locale.ROOT));
        entity.setDiagnostic(json(result.diagnostics()));
        entity.setMatchedAt(CrawlerTime.nowUtc());
        if (existing == null) mapper.insert(entity); else mapper.updateById(entity);
        return entity;
    }

    public ContentPosterMatch find(ContentType contentType, long contentId) {
        return mapper.selectOne(new LambdaQueryWrapper<ContentPosterMatch>()
                .eq(ContentPosterMatch::getContentType, contentType.value())
                .eq(ContentPosterMatch::getContentId, contentId));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Unable to serialize TMDB match diagnostics", error);
        }
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(4, java.math.RoundingMode.HALF_UP);
    }
}
