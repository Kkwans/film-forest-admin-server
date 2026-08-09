package com.filmforest.crawler.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filmforest.common.type.ContentType;
import com.filmforest.crawler.config.CrawlerExecutionProperties;
import com.filmforest.crawler.entity.CrawlerSchedule;
import com.filmforest.crawler.entity.CrawlerTaskLog;
import com.filmforest.crawler.http.FetchCategory;
import com.filmforest.crawler.http.FetchResult;
import com.filmforest.crawler.http.HttpFetcher;
import com.filmforest.crawler.mapper.CrawlerTaskLogMapper;
import com.filmforest.crawler.model.ParseDiagnostics;
import com.filmforest.crawler.model.ParsedContent;
import com.filmforest.crawler.model.SourceListItem;
import com.filmforest.crawler.service.CrawlerScheduleService;
import com.filmforest.crawler.service.CrawlerGenreService;
import com.filmforest.crawler.service.CrawlerSourceItemService;
import com.filmforest.crawler.service.SourceFingerprint;
import com.filmforest.crawler.source.CrawlerSourceAdapter;
import com.filmforest.crawler.source.SourceAdapterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrawlerCoreCrawlModeTest {

    @Mock private CrawlerScheduleService schedules;
    @Mock private CrawlerTaskLogMapper jobs;
    @Mock private SourceAdapterRegistry registry;
    @Mock private HttpFetcher fetcher;
    @Mock private CrawlerContentPersistence persistence;
    @Mock private CrawlerGenreService genres;
    @Mock private CrawlerSourceItemService sourceItems;
    @Mock private CrawlerSourceAdapter adapter;

    private CrawlerExecutionProperties properties;
    private CrawlerCore crawler;

    @BeforeEach
    void setUp() {
        properties = new CrawlerExecutionProperties();
        properties.setLatestConsecutiveUnchanged(20);
        properties.setLatestRecentPages(2);
        crawler = new CrawlerCore(schedules, jobs, registry, fetcher, persistence, genres,
                sourceItems, properties, new ObjectMapper());
    }

    @Test
    void latestRechecksRecentDetailButSkipsPersistenceWhenDetailFingerprintIsUnchanged() {
        AtomicBoolean cancellation = new AtomicBoolean(false);
        URI listUri = URI.create("https://source.test/list/1");
        URI detailUri = URI.create("https://source.test/mv/7.html");
        ParsedContent parsed = parsed(detailUri, "7");
        prepare(latestSchedule(1), job("latest", 5), cancellation);
        when(adapter.listUri(ContentType.MOVIE, 1)).thenReturn(listUri);
        when(fetcher.fetch(eq(listUri), anyMap(), anyInt(), same(cancellation)))
                .thenReturn(success(listUri, "list"));
        SourceListItem item = new SourceListItem("7", detailUri.toString(), "Title", null, 0);
        when(adapter.parseList("list", listUri)).thenReturn(List.of(item));
        when(sourceItems.observeListItem("pkmp4", ContentType.MOVIE, item))
                .thenReturn(new CrawlerSourceItemService.Observation(1L, true, false,
                        SourceFingerprint.forDetail(parsed), 7L, "parsed"));
        when(fetcher.fetch(eq(detailUri), anyMap(), anyInt(), same(cancellation)))
                .thenReturn(success(detailUri, "detail"));
        when(adapter.parseDetail(ContentType.MOVIE, "detail", detailUri)).thenReturn(parsed);

        var summary = crawler.executeCrawl(1L, 9L, cancellation);

        assertThat(summary.discovered()).isEqualTo(1);
        assertThat(summary.fetchSucceeded()).isEqualTo(1);
        assertThat(summary.parseSucceeded()).isEqualTo(1);
        assertThat(summary.unchanged()).isEqualTo(1);
        verify(persistence, never()).persist(eq("pkmp4"), eq(parsed), any());
        verify(sourceItems).recordParsed("pkmp4", ContentType.MOVIE, "7", 7L,
                SourceFingerprint.forDetail(parsed));
    }

    @Test
    void latestUsesListFingerprintShortcutOnlyAfterRecentPages() {
        properties.setLatestRecentPages(1);
        AtomicBoolean cancellation = new AtomicBoolean(false);
        URI listOne = URI.create("https://source.test/list/1");
        URI listTwo = URI.create("https://source.test/list/2");
        URI detailOne = URI.create("https://source.test/mv/1.html");
        URI detailTwo = URI.create("https://source.test/mv/2.html");
        SourceListItem first = new SourceListItem("1", detailOne.toString(), "New", null, 0);
        SourceListItem second = new SourceListItem("2", detailTwo.toString(), "Old", null, 0);
        ParsedContent parsed = parsed(detailOne, "1");
        prepare(latestSchedule(2), job("latest", 8), cancellation);
        when(adapter.listUri(ContentType.MOVIE, 1)).thenReturn(listOne);
        when(adapter.listUri(ContentType.MOVIE, 2)).thenReturn(listTwo);
        when(fetcher.fetch(eq(listOne), anyMap(), anyInt(), same(cancellation)))
                .thenReturn(success(listOne, "one"));
        when(fetcher.fetch(eq(listTwo), anyMap(), anyInt(), same(cancellation)))
                .thenReturn(success(listTwo, "two"));
        when(adapter.parseList("one", listOne)).thenReturn(List.of(first));
        when(adapter.parseList("two", listTwo)).thenReturn(List.of(second));
        when(sourceItems.observeListItem("pkmp4", ContentType.MOVIE, first))
                .thenReturn(new CrawlerSourceItemService.Observation(1L, false, true,
                        null, null, "discovered"));
        when(sourceItems.observeListItem("pkmp4", ContentType.MOVIE, second))
                .thenReturn(new CrawlerSourceItemService.Observation(2L, true, false,
                        "old-detail", 2L, "parsed"));
        when(fetcher.fetch(eq(detailOne), anyMap(), anyInt(), same(cancellation)))
                .thenReturn(success(detailOne, "detail"));
        when(adapter.parseDetail(ContentType.MOVIE, "detail", detailOne)).thenReturn(parsed);
        var resolved = new CrawlerGenreService.ResolvedGenres(List.of(), List.of());
        when(genres.resolve("pkmp4", ContentType.MOVIE, parsed.genres())).thenReturn(resolved);
        when(persistence.persist("pkmp4", parsed, resolved)).thenReturn(
                new CrawlerContentPersistence.PersistResult(true, false, false));

        var summary = crawler.executeCrawl(1L, 9L, cancellation);

        assertThat(summary.discovered()).isEqualTo(2);
        assertThat(summary.added()).isEqualTo(1);
        assertThat(summary.unchanged()).isEqualTo(1);
        verify(fetcher, never()).fetch(eq(detailTwo), anyMap(), anyInt(), same(cancellation));
    }

    @Test
    void fullResumesFromJobCheckpointPageAndIgnoresLatestBatchLimit() {
        AtomicBoolean cancellation = new AtomicBoolean(false);
        CrawlerSchedule schedule = latestSchedule(1);
        schedule.setCrawlMode("full");
        CrawlerTaskLog job = job("full", 7);
        URI listSeven = URI.create("https://source.test/list/7");
        prepare(schedule, job, cancellation);
        when(adapter.listUri(ContentType.MOVIE, 7)).thenReturn(listSeven);
        when(fetcher.fetch(eq(listSeven), anyMap(), anyInt(), same(cancellation)))
                .thenReturn(success(listSeven, "empty"));
        when(adapter.parseList("empty", listSeven)).thenReturn(List.of());

        var summary = crawler.executeCrawl(1L, 9L, cancellation);

        assertThat(summary.discovered()).isZero();
        verify(adapter).listUri(ContentType.MOVIE, 7);
    }

    private void prepare(CrawlerSchedule schedule, CrawlerTaskLog job,
                         AtomicBoolean cancellation) {
        when(schedules.getSchedule(1L)).thenReturn(schedule);
        when(jobs.selectById(9L)).thenReturn(job);
        when(registry.require("pkmp4")).thenReturn(adapter);
        org.mockito.Mockito.lenient().when(adapter.sourceCode()).thenReturn("pkmp4");
        org.mockito.Mockito.lenient().when(genres.resolve(
                org.mockito.ArgumentMatchers.eq("pkmp4"),
                org.mockito.ArgumentMatchers.any(ContentType.class),
                org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(new CrawlerGenreService.ResolvedGenres(List.of(), List.of()));
    }

    private static CrawlerSchedule latestSchedule(int batchSize) {
        CrawlerSchedule schedule = new CrawlerSchedule();
        schedule.setId(1L);
        schedule.setSourceSite("pkmp4");
        schedule.setContentType("movie");
        schedule.setCrawlMode("latest");
        schedule.setBatchSize(batchSize);
        schedule.setRateLimitMs(0);
        return schedule;
    }

    private static CrawlerTaskLog job(String mode, int page) {
        CrawlerTaskLog job = new CrawlerTaskLog();
        job.setId(9L);
        job.setCrawlMode(mode);
        job.setCurrentPage(page);
        return job;
    }

    private static ParsedContent parsed(URI uri, String externalId) {
        return new ParsedContent(externalId, ContentType.MOVIE, uri.toString(), "Title", null,
                2026, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                null, null, null, List.of(), null, null, null, "", null, List.of(),
                new ParseDiagnostics(List.of("h1"), List.of(), List.of(), "page", Map.of()));
    }

    private static FetchResult success(URI uri, String body) {
        return new FetchResult(uri, uri, 200, "text/html", body, 1L,
                FetchCategory.SUCCESS, false, Map.of());
    }
}
