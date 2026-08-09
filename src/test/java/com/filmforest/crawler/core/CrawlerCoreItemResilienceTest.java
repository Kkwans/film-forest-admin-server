package com.filmforest.crawler.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filmforest.common.type.ContentType;
import com.filmforest.crawler.config.CrawlerExecutionProperties;
import com.filmforest.crawler.entity.CrawlerFailureStage;
import com.filmforest.crawler.entity.CrawlerSchedule;
import com.filmforest.crawler.entity.CrawlerTaskLog;
import com.filmforest.crawler.http.FetchCategory;
import com.filmforest.crawler.http.FetchResult;
import com.filmforest.crawler.http.HttpFetcher;
import com.filmforest.crawler.mapper.CrawlerTaskLogMapper;
import com.filmforest.crawler.model.ParseDiagnostics;
import com.filmforest.crawler.model.ParsedContent;
import com.filmforest.crawler.model.SourceListItem;
import com.filmforest.crawler.service.CrawlerGenreService;
import com.filmforest.crawler.service.CrawlerItemFailureService;
import com.filmforest.crawler.service.CrawlerScheduleService;
import com.filmforest.crawler.service.CrawlerSourceItemService;
import com.filmforest.crawler.source.CrawlerSourceAdapter;
import com.filmforest.crawler.source.SourceAdapterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessResourceException;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrawlerCoreItemResilienceTest {

    @Mock private CrawlerScheduleService schedules;
    @Mock private CrawlerTaskLogMapper jobs;
    @Mock private SourceAdapterRegistry registry;
    @Mock private HttpFetcher fetcher;
    @Mock private CrawlerContentPersistence persistence;
    @Mock private CrawlerGenreService genres;
    @Mock private CrawlerSourceItemService sourceItems;
    @Mock private CrawlerItemFailureService itemFailures;
    @Mock private CrawlerSourceAdapter adapter;

    private final AtomicBoolean cancellation = new AtomicBoolean(false);
    private final URI listUri = URI.create("https://source.test/list/1");
    private final URI detailUri = URI.create("https://source.test/mv/7.html");
    private final SourceListItem item = new SourceListItem(
            "7", detailUri.toString(), "Title", null, 0);
    private final ParsedContent parsed = parsed();
    private final CrawlerGenreService.ResolvedGenres resolved =
            new CrawlerGenreService.ResolvedGenres(List.of(5L), List.of("科幻"));
    private CrawlerCore crawler;

    @BeforeEach
    void setUp() {
        CrawlerExecutionProperties properties = new CrawlerExecutionProperties();
        properties.setItemPersistenceMaxAttempts(2);
        properties.setItemRetryBaseDelayMs(0);
        crawler = new CrawlerCore(schedules, jobs, registry, fetcher, persistence, genres,
                sourceItems, itemFailures, properties, new ObjectMapper());
        prepareListItem();
    }

    @Test
    void transientRolledBackPersistenceFailureRetriesOnceThenSucceeds() {
        prepareParsedDetail();
        when(persistence.persist("pkmp4", parsed, resolved, null))
                .thenThrow(new TransientDataAccessResourceException("temporary"))
                .thenReturn(new CrawlerContentPersistence.PersistResult(true, false, false));

        var summary = crawler.executeCrawl(1L, 9L, cancellation);

        assertThat(summary.added()).isEqualTo(1);
        assertThat(summary.failed()).isZero();
        verify(persistence, times(2)).persist("pkmp4", parsed, resolved, null);
        verify(itemFailures, never()).record(any(), any(), any(), any(), any(),
                any(), anyInt(), anyBoolean(), any());
    }

    @Test
    void permanentPersistenceFailureIsNotRetriedAndIsolatedByJobItem() {
        prepareParsedDetail();
        when(persistence.persist("pkmp4", parsed, resolved, null))
                .thenThrow(new DataIntegrityViolationException("invalid row"));

        var summary = crawler.executeCrawl(1L, 9L, cancellation);

        assertThat(summary.failed()).isEqualTo(1);
        verify(persistence).persist("pkmp4", parsed, resolved, null);
        verify(itemFailures).record(9L, "pkmp4", ContentType.MOVIE, item,
                CrawlerFailureStage.PERSISTENCE, "DataIntegrityViolationException",
                1, false, "externalId=7, persistence=DataIntegrityViolationException");
    }

    @Test
    void transientPersistenceFailureStopsAtConfiguredAttemptLimit() {
        prepareParsedDetail();
        when(persistence.persist("pkmp4", parsed, resolved, null))
                .thenThrow(new TransientDataAccessResourceException("temporary one"))
                .thenThrow(new TransientDataAccessResourceException("temporary two"));

        var summary = crawler.executeCrawl(1L, 9L, cancellation);

        assertThat(summary.failed()).isEqualTo(1);
        verify(persistence, times(2)).persist("pkmp4", parsed, resolved, null);
        verify(itemFailures).record(9L, "pkmp4", ContentType.MOVIE, item,
                CrawlerFailureStage.PERSISTENCE, "TransientDataAccessResourceException",
                2, true,
                "externalId=7, persistence=TransientDataAccessResourceException");
    }

    @Test
    void exhaustedHttpAttemptsAreRecordedWithoutAnotherCoreLevelRequestLoop() {
        when(fetcher.fetch(eq(detailUri), anyMap(), anyInt(), same(cancellation)))
                .thenReturn(new FetchResult(detailUri, detailUri, 503, "text/html", "later",
                        20L, FetchCategory.SERVER_ERROR, true, Map.of(), 3));

        var summary = crawler.executeCrawl(1L, 9L, cancellation);

        assertThat(summary.failed()).isEqualTo(1);
        verify(fetcher).fetch(eq(detailUri), anyMap(), anyInt(), same(cancellation));
        verify(persistence, never()).persist(any(), any(), any(), any());
        verify(itemFailures).record(9L, "pkmp4", ContentType.MOVIE, item,
                CrawlerFailureStage.FETCH, "SERVER_ERROR", 3, true,
                "externalId=7, category=SERVER_ERROR");
    }

    private void prepareListItem() {
        CrawlerSchedule schedule = new CrawlerSchedule();
        schedule.setId(1L);
        schedule.setSourceSite("pkmp4");
        schedule.setContentType("movie");
        schedule.setCrawlMode("latest");
        schedule.setBatchSize(1);
        schedule.setRateLimitMs(0);
        CrawlerTaskLog job = new CrawlerTaskLog();
        job.setId(9L);
        job.setCrawlMode("latest");
        job.setCurrentPage(1);
        when(schedules.getSchedule(1L)).thenReturn(schedule);
        when(jobs.selectById(9L)).thenReturn(job);
        when(registry.require("pkmp4")).thenReturn(adapter);
        when(adapter.sourceCode()).thenReturn("pkmp4");
        when(adapter.listUri(ContentType.MOVIE, 1)).thenReturn(listUri);
        when(fetcher.fetch(eq(listUri), anyMap(), anyInt(), same(cancellation)))
                .thenReturn(success(listUri, "list"));
        when(adapter.parseList("list", listUri)).thenReturn(List.of(item));
        when(sourceItems.observeListItem("pkmp4", ContentType.MOVIE, item))
                .thenReturn(new CrawlerSourceItemService.Observation(
                        1L, false, true, null, null, "discovered"));
    }

    private void prepareParsedDetail() {
        when(fetcher.fetch(eq(detailUri), anyMap(), anyInt(), same(cancellation)))
                .thenReturn(success(detailUri, "detail"));
        when(adapter.parseDetail(ContentType.MOVIE, "detail", detailUri)).thenReturn(parsed);
        when(genres.resolve("pkmp4", ContentType.MOVIE, parsed.genres())).thenReturn(resolved);
    }

    private static FetchResult success(URI uri, String body) {
        return new FetchResult(uri, uri, 200, "text/html", body, 1L,
                FetchCategory.SUCCESS, false, Map.of());
    }

    private ParsedContent parsed() {
        return new ParsedContent("7", ContentType.MOVIE, detailUri.toString(), "Title", null,
                2024, List.of(), List.of("科幻片"), List.of(), List.of(), List.of(), List.of(),
                null, null, null, List.of(), null, null, null, "story", null, List.of(),
                new ParseDiagnostics(List.of("h1"), List.of(), List.of(), "fingerprint", Map.of()));
    }
}
