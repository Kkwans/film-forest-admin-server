package com.filmforest.crawler.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filmforest.common.type.ContentType;
import com.filmforest.crawler.config.CrawlerExecutionProperties;
import com.filmforest.crawler.entity.CrawlerCursorState;
import com.filmforest.crawler.entity.CrawlerSchedule;
import com.filmforest.crawler.entity.CrawlerScheduleCursor;
import com.filmforest.crawler.entity.CrawlerTaskLog;
import com.filmforest.crawler.http.FetchCategory;
import com.filmforest.crawler.http.FetchResult;
import com.filmforest.crawler.http.HttpFetcher;
import com.filmforest.crawler.mapper.CrawlerTaskLogMapper;
import com.filmforest.crawler.model.ParseDiagnostics;
import com.filmforest.crawler.model.ParsedContent;
import com.filmforest.crawler.model.CrawlerCheckpoint;
import com.filmforest.crawler.model.CrawlerSourceQuery;
import com.filmforest.crawler.model.SourceListItem;
import com.filmforest.crawler.service.CrawlerScheduleService;
import com.filmforest.crawler.service.CrawlerGenreService;
import com.filmforest.crawler.service.CrawlerItemFailureService;
import com.filmforest.crawler.service.CrawlerScheduleCursorService;
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
import static org.mockito.Mockito.mockingDetails;
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
    @Mock private CrawlerItemFailureService itemFailures;
    @Mock private CrawlerScheduleCursorService cursorService;
    @Mock private CrawlerSourceAdapter adapter;

    private CrawlerExecutionProperties properties;
    private CrawlerCore crawler;

    @BeforeEach
    void setUp() {
        properties = new CrawlerExecutionProperties();
        properties.setLatestConsecutiveUnchanged(20);
        properties.setLatestRecentPages(2);
        crawler = new CrawlerCore(schedules, jobs, registry, fetcher, persistence, genres,
                sourceItems, itemFailures, properties, new ObjectMapper(), cursorService);
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
        verify(persistence, never()).persist(eq("pkmp4"), eq(parsed), any(), any());
        verify(sourceItems).recordParsed("pkmp4", ContentType.MOVIE, "7", 7L,
                SourceFingerprint.forCanonicalContent(ContentType.MOVIE, parsed.title(), parsed.year()),
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
        when(persistence.persist("pkmp4", parsed, resolved, null)).thenReturn(
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

    @Test
    void fullResumeUsesNextExternalIdInsteadOfReplayingCompletedPagePrefix() throws Exception {
        AtomicBoolean cancellation = new AtomicBoolean(false);
        CrawlerSchedule schedule = latestSchedule(1);
        schedule.setCrawlMode("full");
        CrawlerTaskLog job = job("full", 7);
        job.setCheckpoint(new ObjectMapper().writeValueAsString(
                new CrawlerCheckpoint(1, 7, 1, "b", "a")));
        URI listSeven = URI.create("https://source.test/list/7");
        URI listEight = URI.create("https://source.test/list/8");
        SourceListItem itemA = item("a");
        SourceListItem itemB = item("b");
        SourceListItem itemC = item("c");
        ParsedContent parsedB = parsed(URI.create(itemB.sourceUrl()), "b");
        ParsedContent parsedC = parsed(URI.create(itemC.sourceUrl()), "c");
        prepare(schedule, job, cancellation);
        when(adapter.listUri(ContentType.MOVIE, 7)).thenReturn(listSeven);
        when(adapter.listUri(ContentType.MOVIE, 8)).thenReturn(listEight);
        when(fetcher.fetch(eq(listSeven), anyMap(), anyInt(), same(cancellation)))
                .thenReturn(success(listSeven, "page-seven"));
        when(fetcher.fetch(eq(listEight), anyMap(), anyInt(), same(cancellation)))
                .thenReturn(success(listEight, "empty"));
        when(adapter.parseList("page-seven", listSeven)).thenReturn(List.of(itemA, itemB, itemC));
        when(adapter.parseList("empty", listEight)).thenReturn(List.of());
        when(sourceItems.observeListItem(eq("pkmp4"), eq(ContentType.MOVIE), any()))
                .thenReturn(new CrawlerSourceItemService.Observation(
                        1L, false, true, null, null, "discovered"));
        when(fetcher.fetch(eq(URI.create(itemB.sourceUrl())), anyMap(), anyInt(), same(cancellation)))
                .thenReturn(success(URI.create(itemB.sourceUrl()), "detail-b"));
        when(fetcher.fetch(eq(URI.create(itemC.sourceUrl())), anyMap(), anyInt(), same(cancellation)))
                .thenReturn(success(URI.create(itemC.sourceUrl()), "detail-c"));
        when(adapter.parseDetail(ContentType.MOVIE, "detail-b", URI.create(itemB.sourceUrl())))
                .thenReturn(parsedB);
        when(adapter.parseDetail(ContentType.MOVIE, "detail-c", URI.create(itemC.sourceUrl())))
                .thenReturn(parsedC);
        when(persistence.persist(eq("pkmp4"), eq(parsedB), any(), any()))
                .thenReturn(persisted(101L, "key-b"));
        when(persistence.persist(eq("pkmp4"), eq(parsedC), any(), any()))
                .thenReturn(persisted(102L, "key-c"));

        var summary = crawler.executeCrawl(1L, 9L, cancellation);

        assertThat(summary.discovered()).isEqualTo(2);
        assertThat(summary.added()).isEqualTo(2);
        verify(fetcher, never()).fetch(eq(URI.create(itemA.sourceUrl())),
                anyMap(), anyInt(), same(cancellation));
        List<String> checkpoints = mockingDetails(jobs).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("updateProgress"))
                .map(invocation -> (String) invocation.getArgument(11))
                .toList();
        assertThat(checkpoints).isNotEmpty();
        CrawlerCheckpoint last = new ObjectMapper().readValue(
                checkpoints.get(checkpoints.size() - 1), CrawlerCheckpoint.class);
        assertThat(last.nextPage()).isEqualTo(8);
        assertThat(last.nextItemIndex()).isZero();
        assertThat(last.lastCommittedExternalId()).isEqualTo("c");
        assertThat(checkpoints).anySatisfy(json -> {
            try {
                assertThat(new ObjectMapper().readValue(json, CrawlerCheckpoint.class)
                        .nextExternalId()).isEqualTo("b");
            } catch (Exception error) {
                throw new AssertionError(error);
            }
        });
    }

    @Test
    void pageBoundaryCheckpointDoesNotTreatPreviousPageAsMissingAnchor() {
        CrawlerCheckpoint checkpoint = new CrawlerCheckpoint(
                CrawlerCheckpoint.CURRENT_VERSION, 2, 0, null, "last-page-item");

        assertThat(CrawlerCore.anchorMissing(checkpoint, List.of(item("next-page-item"))))
                .isFalse();
    }

    @Test
    void inPageCheckpointStillRequiresNextItemAnchor() {
        CrawlerCheckpoint checkpoint = new CrawlerCheckpoint(
                CrawlerCheckpoint.CURRENT_VERSION, 2, 3, "resume-item", "last-page-item");

        assertThat(CrawlerCore.anchorMissing(checkpoint, List.of(item("other-item"))))
                .isTrue();
    }

    @Test
    void cursorAtPageBoundaryContinuesToNextPageWithoutRecoveryLoop() {
        AtomicBoolean cancellation = new AtomicBoolean(false);
        CrawlerSchedule schedule = latestSchedule(2);
        schedule.setSourceSort("RATING");
        schedule.setTraversalMode("BACKFILL_CONTINUE");
        CrawlerTaskLog job = job("latest", 2);
        job.setSourceSort("RATING");
        job.setTraversalMode("BACKFILL_CONTINUE");
        job.setQueryProfileHash("profile");

        CrawlerScheduleCursor cursor = new CrawlerScheduleCursor();
        cursor.setScheduleId(1L);
        cursor.setProfileHash("profile");
        cursor.setState("ACTIVE");
        cursor.setNextPage(2);
        cursor.setNextItemIndex(0);
        cursor.setLastCommittedExternalId("last-page-item");

        URI listTwo = URI.create("https://source.test/list/2?sort=rating");
        URI listThree = URI.create("https://source.test/list/3?sort=rating");
        SourceListItem next = item("next-page-item");
        ParsedContent parsed = parsed(URI.create(next.sourceUrl()), next.externalId());
        prepare(schedule, job, cancellation);
        when(cursorService.prepare(schedule, job)).thenReturn(cursor);
        when(adapter.listUri(any(CrawlerSourceQuery.class))).thenAnswer(invocation -> {
            CrawlerSourceQuery query = invocation.getArgument(0);
            return query.page() == 2 ? listTwo : listThree;
        });
        when(fetcher.fetch(eq(listTwo), anyMap(), anyInt(), same(cancellation)))
                .thenReturn(success(listTwo, "page-two"));
        when(fetcher.fetch(eq(listThree), anyMap(), anyInt(), same(cancellation)))
                .thenReturn(success(listThree, "empty"));
        when(adapter.parseList("page-two", listTwo)).thenReturn(List.of(next));
        when(adapter.parseList("empty", listThree)).thenReturn(List.of());
        when(sourceItems.observeListItem("pkmp4", ContentType.MOVIE, next))
                .thenReturn(new CrawlerSourceItemService.Observation(
                        null, false, true, null, null, "discovered"));
        when(fetcher.fetch(eq(URI.create(next.sourceUrl())), anyMap(), anyInt(), same(cancellation)))
                .thenReturn(success(URI.create(next.sourceUrl()), "detail"));
        when(adapter.parseDetail(ContentType.MOVIE, "detail", URI.create(next.sourceUrl())))
                .thenReturn(parsed);
        CrawlerGenreService.ResolvedGenres resolved = new CrawlerGenreService.ResolvedGenres(List.of(), List.of());
        when(genres.resolve("pkmp4", ContentType.MOVIE, parsed.genres())).thenReturn(resolved);
        when(persistence.persist("pkmp4", parsed, resolved, null)).thenReturn(persisted(101L, "next"));

        var summary = crawler.executeCrawl(1L, 9L, cancellation);

        assertThat(summary.discovered()).isEqualTo(1);
        assertThat(summary.added()).isEqualTo(1);
        verify(fetcher, never()).fetch(eq(URI.create("https://source.test/list/1?sort=rating")),
                anyMap(), anyInt(), same(cancellation));
        verify(fetcher).fetch(eq(listTwo), anyMap(), anyInt(), same(cancellation));
        verify(cursorService).mark(cursor, CrawlerCursorState.COMPLETE, null);
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

    private static SourceListItem item(String externalId) {
        return new SourceListItem(externalId,
                "https://source.test/mv/" + externalId + ".html", "Title " + externalId,
                null, 0);
    }

    private static CrawlerContentPersistence.PersistResult persisted(long contentId,
                                                                      String canonicalKey) {
        return new CrawlerContentPersistence.PersistResult(contentId, canonicalKey,
                true, false, false,
                new CrawlerResourceDiffService.ResourceDiffResult(0, 0, 0, 0, false));
    }

    private static FetchResult success(URI uri, String body) {
        return new FetchResult(uri, uri, 200, "text/html", body, 1L,
                FetchCategory.SUCCESS, false, Map.of());
    }
}
