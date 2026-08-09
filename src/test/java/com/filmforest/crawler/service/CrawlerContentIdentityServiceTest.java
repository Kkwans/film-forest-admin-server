package com.filmforest.crawler.service;

import com.filmforest.common.type.ContentType;
import com.filmforest.crawler.entity.CrawlerContentIdentity;
import com.filmforest.crawler.mapper.CrawlerContentIdentityMapper;
import com.filmforest.crawler.model.ParseDiagnostics;
import com.filmforest.crawler.model.ParsedContent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CrawlerContentIdentityServiceTest {

    @Test
    void reservesGeneratedIdentityForFirstCanonicalContent() {
        CrawlerContentIdentityMapper mapper = mock(CrawlerContentIdentityMapper.class);
        when(mapper.reserve(any())).thenAnswer(invocation -> {
            invocation.<CrawlerContentIdentity>getArgument(0).setId(77L);
            return 1;
        });
        CrawlerContentIdentityService service = new CrawlerContentIdentityService(mapper);

        var identity = service.resolve(parsed("source-a"), null);

        assertThat(identity.contentId()).isEqualTo(77L);
        assertThat(identity.normalizedTitle()).isEqualTo("示例电影forest");
        assertThat(identity.canonicalKey()).hasSize(64);
    }

    @Test
    void differentExternalIdsReuseExistingCanonicalIdentity() {
        CrawlerContentIdentityMapper mapper = mock(CrawlerContentIdentityMapper.class);
        ParsedContent content = parsed("source-b");
        String key = SourceFingerprint.forCanonicalContent(
                content.contentType(), content.title(), content.year());
        CrawlerContentIdentity existing = new CrawlerContentIdentity();
        existing.setId(77L);
        existing.setContentType("movie");
        existing.setCanonicalKey(key);
        existing.setNormalizedTitle("示例电影forest");
        existing.setReleaseYear(2026);
        when(mapper.selectByCanonicalKey("movie", key)).thenReturn(existing);
        CrawlerContentIdentityService service = new CrawlerContentIdentityService(mapper);

        var identity = service.resolve(content, null);

        assertThat(identity.contentId()).isEqualTo(77L);
        verify(mapper, never()).reserve(any());
    }

    private static ParsedContent parsed(String externalId) {
        return new ParsedContent(externalId, ContentType.MOVIE,
                "https://source.test/mv/" + externalId, "示例电影：Forest (2026)",
                null, 2026, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), null, null, null, List.of(), null, null, null, "简介",
                null, List.of(),
                new ParseDiagnostics(List.of("h1"), List.of(), List.of(), "page", Map.of()));
    }
}
