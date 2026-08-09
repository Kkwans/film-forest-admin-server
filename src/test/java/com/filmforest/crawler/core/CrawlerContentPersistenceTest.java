package com.filmforest.crawler.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filmforest.common.type.ContentType;
import com.filmforest.content.entity.Movie;
import com.filmforest.content.service.AnimeService;
import com.filmforest.content.service.DramaService;
import com.filmforest.content.service.MovieService;
import com.filmforest.content.service.ShortDramaService;
import com.filmforest.content.service.VarietyService;
import com.filmforest.crawler.model.ParseDiagnostics;
import com.filmforest.crawler.model.ParsedContent;
import com.filmforest.crawler.service.CrawlerGenreService;
import com.filmforest.crawler.service.CrawlerContentIdentityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrawlerContentPersistenceTest {

    @Mock private MovieService movieService;
    @Mock private DramaService dramaService;
    @Mock private VarietyService varietyService;
    @Mock private AnimeService animeService;
    @Mock private ShortDramaService shortDramaService;
    @Mock private CrawlerResourceDiffService resourceDiffService;
    @Mock private CrawlerGenreService genreService;
    @Mock private CrawlerContentIdentityService identityService;

    private CrawlerContentPersistence persistence;

    @BeforeEach
    void setUp() {
        persistence = new CrawlerContentPersistence(movieService, dramaService, varietyService,
                animeService, shortDramaService, resourceDiffService, genreService,
                identityService, new ObjectMapper());
        when(resourceDiffService.apply("pkmp4", "movie", 42L, List.of()))
                .thenReturn(new CrawlerResourceDiffService.ResourceDiffResult(0, 0, 0, 0, false));
    }

    @Test
    void newCrawlerContentUsesDraftAndStandardGenreProjection() {
        var genres = new CrawlerGenreService.ResolvedGenres(List.of(5L), List.of("科幻"));
        when(identityService.resolve(parsed(), null)).thenReturn(
                new CrawlerContentIdentityService.Identity(42L, "canonical-key", "示例电影", 2026));

        persistence.persist("pkmp4", parsed(), genres, null);

        ArgumentCaptor<Movie> captor = ArgumentCaptor.forClass(Movie.class);
        verify(movieService).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isZero();
        assertThat(captor.getValue().getGenre()).isEqualTo("[\"科幻\"]");
        assertThat(captor.getValue().getLanguage()).isEqualTo("[\"英语\"]");
        assertThat(captor.getValue().getScoreRt()).isEqualByComparingTo("8.7");
        verify(genreService).replaceContentGenres(42L, ContentType.MOVIE, genres);
    }

    @Test
    void crawlerUpdateDoesNotRepublishExistingOfflineContent() {
        Movie existing = new Movie();
        existing.setId(42L);
        existing.setStatus(2);
        when(movieService.getById(42L)).thenReturn(existing);

        when(identityService.resolve(parsed(), 42L)).thenReturn(
                new CrawlerContentIdentityService.Identity(42L, "canonical-key", "示例电影", 2026));
        persistence.persist("pkmp4", parsed(),
                new CrawlerGenreService.ResolvedGenres(List.of(), List.of()), 42L);

        ArgumentCaptor<Movie> captor = ArgumentCaptor.forClass(Movie.class);
        verify(movieService).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isNull();
    }

    private static ParsedContent parsed() {
        return new ParsedContent("42", ContentType.MOVIE, "https://source.test/mv/42.html",
                "示例电影 (2026)", "/poster.jpg", 2026, List.of("美国"),
                List.of("科幻片", "英语"), List.of("导演"), List.of("编剧"),
                List.of("主演"), List.of("英语"), 120, null, "2026-01-01",
                List.of("别名"), new BigDecimal("8.5"), new BigDecimal("8.3"),
                new BigDecimal("8.7"), "简介", null, List.of(),
                new ParseDiagnostics(List.of("h1"), List.of(), List.of(), "page", Map.of()));
    }
}
