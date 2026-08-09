package com.filmforest.crawler.source.pkmp4;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filmforest.common.type.ContentType;
import com.filmforest.crawler.http.FetchCategory;
import com.filmforest.crawler.http.FetchResult;
import com.filmforest.crawler.http.HttpFetcher;
import com.filmforest.crawler.model.ParseDiagnostics;
import com.filmforest.crawler.model.ParsedContent;
import com.filmforest.crawler.model.ParsedResource;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Pkmp4PlaybackEnricherTest {

    @Test
    void resolvesTrustedPlayerPageAndKeepsItAsFallbackIdentity() {
        URI page = URI.create("https://www.pkmp4.xyz/py/42-1-1.html");
        AtomicBoolean cancellation = new AtomicBoolean();
        HttpFetcher fetcher = mock(HttpFetcher.class);
        when(fetcher.fetch(eq(page), anyMap(), anyInt(), same(cancellation)))
                .thenReturn(new FetchResult(page, page, 200, "text/html",
                        "<script>var player_aaaa={\"url\":\"https://cdn.example.test/42.m3u8\"}</script>",
                        10, FetchCategory.SUCCESS, false, Map.of()));

        ParsedResource resource = new ParsedResource(ParsedResource.Kind.ONLINE, "天堂 · HD",
                page.toString(), null, null, null, false, false, 1, null, "HD", 0, "HD",
                page.toString(), "EXTERNAL_PAGE");
        ParsedContent parsed = new ParsedContent("42", ContentType.MOVIE,
                "https://www.pkmp4.xyz/mv/42.html", "示例", null, 2024,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null,
                null, null, List.of(), null, null, null, "简介", null, List.of(resource),
                new ParseDiagnostics(List.of(), List.of(), List.of(), "fingerprint", Map.of()));

        ParsedResource enriched = new Pkmp4PlaybackEnricher(
                new Pkmp4PlaybackPageParser(new ObjectMapper()))
                .enrich(parsed, fetcher, 100, cancellation).resources().get(0);

        assertThat(enriched.url()).isEqualTo("https://cdn.example.test/42.m3u8");
        assertThat(enriched.sourcePageUrl()).isEqualTo(page.toString());
        assertThat(enriched.playbackType()).isEqualTo("HLS");
        verify(fetcher).fetch(eq(page), anyMap(),
                eq(Pkmp4PlaybackEnricher.MIN_PLAYBACK_PAGE_DELAY_MS), same(cancellation));
    }
}
