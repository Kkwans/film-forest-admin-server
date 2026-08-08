package com.filmforest.crawler.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filmforest.common.type.ContentType;
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
import com.filmforest.crawler.source.CrawlerSourceAdapter;
import com.filmforest.crawler.source.SourceAdapterRegistry;
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

class CrawlerCoreAdapterRoutingTest {

    @Test
    void sourceSiteSelectsAdapterBeforeFetcherAndTypedPersistence() {
        CrawlerScheduleService schedules = mock(CrawlerScheduleService.class);
        CrawlerTaskLogMapper jobs = mock(CrawlerTaskLogMapper.class);
        SourceAdapterRegistry registry = mock(SourceAdapterRegistry.class);
        HttpFetcher fetcher = mock(HttpFetcher.class);
        CrawlerContentPersistence persistence = mock(CrawlerContentPersistence.class);
        CrawlerSourceAdapter adapter = mock(CrawlerSourceAdapter.class);
        CrawlerCore crawler = new CrawlerCore(schedules, jobs, registry, fetcher, persistence,
                new ObjectMapper());

        CrawlerSchedule schedule = new CrawlerSchedule();
        schedule.setId(1L);
        schedule.setSourceSite("pkmp4");
        schedule.setContentType("movie");
        schedule.setBatchSize(1);
        schedule.setRateLimitMs(0);
        CrawlerTaskLog job = new CrawlerTaskLog();
        job.setId(9L);
        job.setCurrentPage(1);
        URI listUri = URI.create("https://source.test/list");
        URI detailUri = URI.create("https://source.test/mv/7.html");
        AtomicBoolean cancellation = new AtomicBoolean(false);

        when(schedules.getSchedule(1L)).thenReturn(schedule);
        when(jobs.selectById(9L)).thenReturn(job);
        when(registry.require("pkmp4")).thenReturn(adapter);
        when(adapter.sourceCode()).thenReturn("pkmp4");
        when(adapter.listUri(ContentType.MOVIE, 1)).thenReturn(listUri);
        when(fetcher.fetch(eq(listUri), anyMap(), anyInt(), same(cancellation)))
                .thenReturn(success(listUri, "list"));
        when(adapter.parseList("list", listUri)).thenReturn(List.of(
                new SourceListItem("7", detailUri.toString(), "Title", null, 0)));
        when(fetcher.fetch(eq(detailUri), anyMap(), anyInt(), same(cancellation)))
                .thenReturn(success(detailUri, "detail"));
        ParsedContent parsed = parsedMovie(detailUri);
        when(adapter.parseDetail(ContentType.MOVIE, "detail", detailUri)).thenReturn(parsed);
        when(persistence.persist(parsed)).thenReturn(
                new CrawlerContentPersistence.PersistResult(true, false, false));

        var summary = crawler.executeCrawl(1L, 9L, cancellation);

        assertThat(summary.discovered()).isEqualTo(1);
        assertThat(summary.fetchSucceeded()).isEqualTo(1);
        assertThat(summary.parseSucceeded()).isEqualTo(1);
        assertThat(summary.added()).isEqualTo(1);
        verify(registry).require("pkmp4");
        verify(persistence).persist(parsed);
    }

    private static FetchResult success(URI uri, String body) {
        return new FetchResult(uri, uri, 200, "text/html", body, 1L,
                FetchCategory.SUCCESS, false, Map.of());
    }

    private static ParsedContent parsedMovie(URI uri) {
        return new ParsedContent("7", ContentType.MOVIE, uri.toString(), "Title", null,
                2024, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                null, null, null, List.of(), null, null, null, "", null, List.of(),
                new ParseDiagnostics(List.of("h1"), List.of(), List.of(), "fingerprint", Map.of()));
    }
}
