package com.filmforest.crawler.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.filmforest.common.type.ContentType;
import com.filmforest.crawler.entity.CrawlerSchedule;
import com.filmforest.crawler.entity.CrawlerTaskLog;
import com.filmforest.crawler.http.FetchCategory;
import com.filmforest.crawler.http.FetchResult;
import com.filmforest.crawler.http.HttpFetcher;
import com.filmforest.crawler.mapper.CrawlerTaskLogMapper;
import com.filmforest.crawler.model.ParsedContent;
import com.filmforest.crawler.model.SourceListItem;
import com.filmforest.crawler.service.CrawlExecutionSummary;
import com.filmforest.crawler.service.CrawlerScheduleService;
import com.filmforest.crawler.service.CrawlerTime;
import com.filmforest.crawler.source.CrawlerSourceAdapter;
import com.filmforest.crawler.source.SourceAdapterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class CrawlerCore {

    private static final int STRUCTURE_FAILURE_THRESHOLD = 3;

    private final CrawlerScheduleService scheduleService;
    private final CrawlerTaskLogMapper taskLogMapper;
    private final SourceAdapterRegistry sourceAdapterRegistry;
    private final HttpFetcher httpFetcher;
    private final CrawlerContentPersistence contentPersistence;
    private final ObjectMapper objectMapper;
    private final ThreadLocal<Long> executingJobId = new ThreadLocal<>();

    public CrawlerCore(CrawlerScheduleService scheduleService,
                       CrawlerTaskLogMapper taskLogMapper,
                       SourceAdapterRegistry sourceAdapterRegistry,
                       HttpFetcher httpFetcher,
                       CrawlerContentPersistence contentPersistence,
                       ObjectMapper objectMapper) {
        this.scheduleService = scheduleService;
        this.taskLogMapper = taskLogMapper;
        this.sourceAdapterRegistry = sourceAdapterRegistry;
        this.httpFetcher = httpFetcher;
        this.contentPersistence = contentPersistence;
        this.objectMapper = objectMapper;
    }

    public CrawlExecutionSummary executeCrawl(Long scheduleId, Long logId, AtomicBoolean cancellation) {
        CrawlerSchedule schedule = scheduleService.getSchedule(scheduleId);
        CrawlerTaskLog job = taskLogMapper.selectById(logId);
        if (schedule == null || job == null) {
            throw new IllegalArgumentException("Crawler schedule or job does not exist");
        }
        ContentType contentType = parseContentType(schedule.getContentType());
        if (isCancellationRequested(cancellation)) {
            return emptySummary();
        }
        CrawlerSourceAdapter adapter = sourceAdapterRegistry.require(schedule.getSourceSite());
        int startPage = job.getCurrentPage() == null ? 1 : Math.max(1, job.getCurrentPage());
        int maxItems = schedule.getBatchSize() == null ? 20 : Math.max(1, schedule.getBatchSize());
        int rateLimitMs = schedule.getRateLimitMs() == null ? 0 : Math.max(0, schedule.getRateLimitMs());
        Set<String> genreFilter = parseGenreFilter(schedule.getGenreFilter());

        executingJobId.set(logId);
        try {
            return crawl(scheduleId, adapter, contentType, startPage, maxItems, rateLimitMs,
                    genreFilter, cancellation);
        } finally {
            executingJobId.remove();
        }
    }

    private CrawlExecutionSummary crawl(Long scheduleId, CrawlerSourceAdapter adapter,
                                        ContentType contentType, int startPage, int maxItems,
                                        int rateLimitMs, Set<String> genreFilter,
                                        AtomicBoolean cancellation) {
        MutableStats stats = new MutableStats();
        int page = startPage;
        int consecutiveStructureFailures = 0;
        while (stats.discovered < maxItems && !isCancellationRequested(cancellation)) {
            URI listUri = adapter.listUri(contentType, page);
            FetchResult listFetch = httpFetcher.fetch(listUri, Map.of(), rateLimitMs, cancellation);
            if (listFetch.category() == FetchCategory.CANCELLED) {
                break;
            }
            if (!listFetch.successful()) {
                throw new CrawlerFetchException("List fetch failed", listFetch);
            }
            List<SourceListItem> items = adapter.parseList(listFetch.body(), listFetch.finalUrl());
            if (items.isEmpty()) {
                break;
            }

            boolean pageCompleted = true;
            for (SourceListItem item : items) {
                if (stats.discovered >= maxItems) {
                    pageCompleted = false;
                    break;
                }
                if (isCancellationRequested(cancellation)) {
                    pageCompleted = false;
                    break;
                }
                recordProgress(page, item.sourceUrl(), stats);
                ItemProcessingResult result = processItem(adapter, contentType, item, rateLimitMs,
                        genreFilter, cancellation, stats);
                if (result.outcome() == ItemOutcome.STRUCTURE_FAILURE) {
                    consecutiveStructureFailures++;
                    if (consecutiveStructureFailures >= STRUCTURE_FAILURE_THRESHOLD) {
                        throw new CrawlerSourceStructureException(adapter.sourceCode(),
                                consecutiveStructureFailures, result.diagnostic());
                    }
                } else if (result.outcome() == ItemOutcome.SUCCESS
                        || result.outcome() == ItemOutcome.FILTERED) {
                    consecutiveStructureFailures = 0;
                }
                recordProgress(page, item.sourceUrl(), stats);
            }
            recordProgress(pageCompleted ? page + 1 : page, null, stats);
            if (!pageCompleted) {
                break;
            }
            page++;
        }
        if (isCancellationRequested(cancellation)) {
            recordProgress(page, null, stats);
        }
        return stats.toSummary();
    }

    private ItemProcessingResult processItem(CrawlerSourceAdapter adapter, ContentType contentType,
                                             SourceListItem item, int rateLimitMs,
                                             Set<String> genreFilter, AtomicBoolean cancellation,
                                             MutableStats stats) {
        FetchResult detailFetch = httpFetcher.fetch(URI.create(item.sourceUrl()), Map.of(),
                rateLimitMs, cancellation);
        if (detailFetch.category() == FetchCategory.CANCELLED) {
            return new ItemProcessingResult(ItemOutcome.CANCELLED, "cancelled");
        }
        stats.discovered++;
        if (!detailFetch.successful()) {
            stats.failed++;
            log.atWarn().log("Detail fetch failed: source={}, externalId={}, category={}",
                    adapter.sourceCode(), item.externalId(), detailFetch.category());
            ItemOutcome outcome = switch (detailFetch.category()) {
                case CHALLENGE_PAGE, INVALID_CONTENT_TYPE, EMPTY_BODY -> ItemOutcome.STRUCTURE_FAILURE;
                default -> ItemOutcome.FETCH_FAILURE;
            };
            return new ItemProcessingResult(outcome,
                    "externalId=" + item.externalId() + ", category=" + detailFetch.category());
        }
        stats.fetchSucceeded++;
        ParsedContent parsed;
        try {
            parsed = adapter.parseDetail(contentType, detailFetch.body(), detailFetch.finalUrl());
        } catch (RuntimeException parseFailure) {
            stats.failed++;
            return new ItemProcessingResult(ItemOutcome.STRUCTURE_FAILURE,
                    "externalId=" + item.externalId() + ", parser="
                            + parseFailure.getClass().getSimpleName());
        }
        if (!parsed.valid()) {
            stats.failed++;
            String diagnostic = "externalId=" + item.externalId() + ", missing="
                    + parsed.diagnostics().missingRequiredFields() + ", fingerprint="
                    + parsed.diagnostics().pageFingerprint();
            log.atWarn().log("Detail parse rejected: source={}, {}", adapter.sourceCode(), diagnostic);
            return new ItemProcessingResult(ItemOutcome.STRUCTURE_FAILURE, diagnostic);
        }
        stats.parseSucceeded++;
        if (!matchesGenreFilter(parsed.genres(), genreFilter)) {
            stats.filtered++;
            return new ItemProcessingResult(ItemOutcome.FILTERED, "filtered");
        }
        try {
            CrawlerContentPersistence.PersistResult persisted = contentPersistence.persist(
                    adapter.sourceCode(), parsed);
            if (persisted.added()) stats.added++;
            if (persisted.updated()) stats.updated++;
            if (persisted.unchanged()) stats.unchanged++;
            return new ItemProcessingResult(ItemOutcome.SUCCESS, "ok");
        } catch (RuntimeException persistenceFailure) {
            stats.failed++;
            log.warn("Detail persistence failed: source={}, externalId={}, error={}",
                    adapter.sourceCode(), item.externalId(), persistenceFailure.getClass().getSimpleName());
            return new ItemProcessingResult(ItemOutcome.PERSISTENCE_FAILURE,
                    "externalId=" + item.externalId() + ", persistence="
                            + persistenceFailure.getClass().getSimpleName());
        }
    }

    private void recordProgress(int currentPage, String currentItem, MutableStats stats) {
        Long jobId = executingJobId.get();
        if (jobId == null) return;
        try {
            String checkpoint = objectMapper.writeValueAsString(Map.of("nextPage", currentPage));
            taskLogMapper.updateProgress(jobId, currentPage, currentItem,
                    stats.discovered, stats.fetchSucceeded, stats.parseSucceeded,
                    stats.added, stats.updated, stats.unchanged, stats.filtered, stats.failed,
                    checkpoint, CrawlerTime.nowUtc());
        } catch (Exception error) {
            log.warn("Failed to update crawler progress: jobId={}, error={}",
                    jobId, error.getClass().getSimpleName());
        }
    }

    private Set<String> parseGenreFilter(String value) {
        if (value == null || value.isBlank()) return Set.of();
        try {
            List<String> genres = objectMapper.readValue(value, new TypeReference<>() { });
            return Set.copyOf(genres);
        } catch (Exception invalidFilter) {
            log.warn("Ignoring invalid crawler genre filter");
            return Set.of();
        }
    }

    private static boolean matchesGenreFilter(List<String> genres, Set<String> filter) {
        if (filter.isEmpty()) return true;
        return new HashSet<>(genres).stream().anyMatch(filter::contains);
    }

    private static ContentType parseContentType(String value) {
        if ("short".equals(value)) return ContentType.SHORT_DRAMA;
        return ContentType.fromValue(value)
                .orElseThrow(() -> new IllegalArgumentException("Unknown contentType: " + value));
    }

    private static CrawlExecutionSummary emptySummary() {
        return new CrawlExecutionSummary(0, 0, 0, 0, 0, 0, 0, 0);
    }

    private static boolean sleepWithCancellation(long delayMs, AtomicBoolean cancellation) {
        long remaining = delayMs;
        while (remaining > 0) {
            if (isCancellationRequested(cancellation)) return false;
            long slice = Math.min(remaining, 100L);
            try {
                Thread.sleep(slice);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                if (cancellation != null) cancellation.set(true);
                return false;
            }
            remaining -= slice;
        }
        return !isCancellationRequested(cancellation);
    }

    private static boolean isCancellationRequested(AtomicBoolean cancellation) {
        return cancellation != null && cancellation.get();
    }

    private static final class MutableStats {
        private int discovered;
        private int fetchSucceeded;
        private int parseSucceeded;
        private int added;
        private int updated;
        private int unchanged;
        private int filtered;
        private int failed;

        private CrawlExecutionSummary toSummary() {
            return new CrawlExecutionSummary(discovered, fetchSucceeded, parseSucceeded,
                    added, updated, unchanged, filtered, failed);
        }
    }

    private enum ItemOutcome {
        SUCCESS,
        FILTERED,
        FETCH_FAILURE,
        STRUCTURE_FAILURE,
        PERSISTENCE_FAILURE,
        CANCELLED
    }

    private record ItemProcessingResult(ItemOutcome outcome, String diagnostic) { }
}
