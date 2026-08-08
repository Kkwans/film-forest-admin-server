package com.filmforest.poster.tmdb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filmforest.crawler.http.FetchCategory;
import com.filmforest.crawler.http.FetchResult;
import com.filmforest.crawler.http.HttpFetcher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TmdbApiClientTest {

    @Test
    void apiKeyAuthenticationDeclaresSensitiveQueryAndParsesMovieSearchContract() {
        HttpFetcher fetcher = mock(HttpFetcher.class);
        URI publicUri = URI.create("https://api.themoviedb.org/3/search/movie?api_key=REDACTED");
        String body = """
                {"results":[{"id":11,"title":"电影","original_title":"Movie",
                "release_date":"2024-01-02","poster_path":"/poster.jpg","original_language":"zh"}]}
                """;
        when(fetcher.fetch(any(URI.class), anyMap(), anyInt(), any(AtomicBoolean.class),
                eq(Set.of("api_key")))).thenReturn(new FetchResult(publicUri, publicUri, 200,
                "application/json", body, 1L, FetchCategory.SUCCESS, false, Map.of()));
        TmdbApiClient client = new TmdbApiClient(fetcher, new ObjectMapper());

        var results = client.search(TmdbMediaType.MOVIE, "电影", 2024,
                new TmdbCredential(TmdbCredential.Type.API_KEY, "fixture-api-key"));

        assertThat(results).singleElement().satisfies(candidate -> {
            assertThat(candidate.id()).isEqualTo(11L);
            assertThat(candidate.title()).isEqualTo("电影");
            assertThat(candidate.year()).isEqualTo(2024);
        });
        ArgumentCaptor<URI> uri = ArgumentCaptor.forClass(URI.class);
        verify(fetcher).fetch(uri.capture(), anyMap(), anyInt(), any(AtomicBoolean.class),
                eq(Set.of("api_key")));
        assertThat(uri.getValue().getPath()).isEqualTo("/3/search/movie");
        assertThat(uri.getValue().getRawQuery()).contains("primary_release_year=2024", "api_key=");
    }
}
