package com.filmforest.crawler.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.filmforest.common.type.ContentType;
import com.filmforest.crawler.config.CrawlerExecutionProperties;
import com.filmforest.crawler.entity.CrawlerCrawlMode;
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
import com.filmforest.crawler.service.CrawlerSourceItemService;
import com.filmforest.crawler.service.CrawlerTime;
import com.filmforest.crawler.service.SourceFingerprint;
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
    private final CrawlerSourceItemService sourceItemService;
    private final CrawlerExecutionProperties executionProperties;
    private final ObjectMapper objectMapper;
    private final ThreadLocal<Long> executingJobId = new ThreadLocal<>();

    public CrawlerCore(CrawlerScheduleService scheduleService,
                       CrawlerTaskLogMapper taskLogMapper,
                       SourceAdapterRegistry sourceAdapterRegistry,
                       HttpFetcher httpFetcher,
                       CrawlerContentPersistence contentPersistence,
                       CrawlerSourceItemService sourceItemService,
                       CrawlerExecutionProperties executionProperties,
                       ObjectMapper objectMapper) {
        this.scheduleService = scheduleService;
        this.taskLogMapper = taskLogMapper;
        this.sourceAdapterRegistry = sourceAdapterRegistry;
        this.httpFetcher = httpFetcher;
        this.contentPersistence = contentPersistence;
        this.sourceItemService = sourceItemService;
        this.executionProperties = executionProperties;
        this.objectMapper = objectMapper;
    }

    public CrawlExecutionSummary executeCrawl(Long scheduleId, Long logId,
                                              AtomicBoolean cancellation) {
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
        CrawlerCrawlMode crawlMode = CrawlerCrawlMode.fromCode(job.getCrawlMode() == null
                ? schedule.getCrawlMode() : job.getCrawlMode());
        int startPage = crawlMode == CrawlerCrawlMode.FULL && job.getCurrentPage() != null
                ? Math.max(1, job.getCurrentPage()) : 1;
        int maxItems = crawlMode == CrawlerCrawlMode.FULL ? Integer.MAX_VALUE
                : schedule.getBatchSize() == null ? 20 : Math.max(1, schedule.getBatchSize());
        int rateLimitMs = schedule.getRateLimitMs() == null
                ? 0 : Math.max(0, schedule.getRateLimitMs());
        Set<String> genreFilter = parseGenreFilter(schedule.getGenreFilter());

        executingJobId.set(logId);
        try {
            return crawl(scheduleId, adapter, contentType, crawlMode, startPage, maxItems,
                    rateLimitMs, genreFilter, cancellation);
        } finally {
            executingJobId.remove();
        }
    }

    private CrawlExecutionSummary crawl(Long scheduleId, CrawlerSourceAdapter adapter,
                                        ContentType contentType, CrawlerCrawlMode crawlMode,
                                        int startPage, int maxItems, int rateLimitMs,
                                        Set<String> genreFilter, AtomicBoolean cancellation) {
        MutableStats stats = new MutableStats();
        int page = startPage;
        int consecutiveStructureFailures = 0;
        int consecutiveOldItems = 0;
        int latestStopThreshold = Math.max(1,
                executionProperties.getLatestConsecutiveUnchanged());
        int latestRecentPages = Math.max(1, executionProperties.getLatestRecentPages());
        boolean latestBoundaryReached = false;
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
                if (stats.discovered >= maxItems || isCancellationRequested(cancellation)) {
                    pageCompleted = false;
                    break;
                }
                CrawlerSourceItemService.Observation observation = sourceItemService.observeListItem(
                        adapter.sourceCode(), contentType, item);
                stats.discovered++;
                recordProgress(page, item.sourceUrl(), stats);
                ItemProcessingResult result = processItem(adapter, contentType, item, rateLimitMs,
                        genreFilter, cancellation, stats, observation,
                        crawlMode == CrawlerCrawlMode.LATEST && page > latestRecentPages);
                if (result.outcome() == ItemOutcome.STRUCTURE_FAILURE) {
                    consecutiveStructureFailures++;
                    if (consecutiveStructureFailures >= STRUCTURE_FAILURE_THRESHOLD) {
                        throw new CrawlerSourceStructureException(adapter.sourceCode(),
                                consecutiveStructureFailures, result.diagnostic());
                    }
                } else if (result.outcome() == ItemOutcome.SUCCESS
                        || result.outcome() == ItemOutcome.UNCHANGED
                        || result.outcome() == ItemOutcome.FILTERED) {
                    consecutiveStructureFailures = 0;
                }
                consecutiveOldItems = result.oldItem() ? consecutiveOldItems + 1 : 0;
                recordProgress(page, item.sourceUrl(), stats);
                if (crawlMode == CrawlerCrawlMode.LATEST && page >= latestRecentPages
                        && consecutiveOldItems >= latestStopThreshold) {
                    pageCompleted = false;
                    latestBoundaryReached = true;
                    break;
                }
            }
            recordProgress(pageCompleted ? page + 1 : page, null, stats);
            if (!pageCompleted) {
                break;
            }
            page++;
        }
        if (latestBoundaryReached) {
            log.info("LATEST crawl reached unchanged boundary: scheduleId={}, page={}, consecutiveOld={}",
                    scheduleId, page, consecutiveOldItems);
        }
        if (isCancellationRequested(cancellation)) {
            recordProgress(page, null, stats);
        }
        return stats.toSummary();
    }

    private ItemProcessingResult processItem(CrawlerSourceAdapter adapter, ContentType contentType,
                                             SourceListItem item, int rateLimitMs,
                                             Set<String> genreFilter, AtomicBoolean cancellation,
                                             MutableStats stats,
                                             CrawlerSourceItemService.Observation observation,
                                             boolean allowListFingerprintShortcut) {
        if (allowListFingerprintShortcut && observation.knownBefore()
                && !observation.listChanged() && observation.previousDetailFingerprint() != null) {
            if ("filtered".equals(observation.previousParseStatus())) {
                stats.filtered++;
                return new ItemProcessingResult(ItemOutcome.FILTERED, "list-unchanged", true);
            }
            if ("parsed".equals(observation.previousParseStatus())
                    && observation.internalContentId() != null) {
                stats.unchanged++;
                return new ItemProcessingResult(ItemOutcome.UNCHANGED, "list-unchanged", true);
            }
        }

        FetchResult detailFetch = httpFetcher.fetch(URI.create(item.sourceUrl()), Map.of(),
                rateLimitMs, cancellation);
        if (detailFetch.category() == FetchCategory.CANCELLED) {
            return new ItemProcessingResult(ItemOutcome.CANCELLED, "cancelled", false);
        }
        if (!detailFetch.successful()) {
            stats.failed++;
            sourceItemService.recordFetchFailure(adapter.sourceCode(), contentType,
                    item.externalId(), detailFetch.category().name());
            log.atWarn().log("Detail fetch failed: source={}, externalId={}, category={}",
                    adapter.sourceCode(), item.externalId(), detailFetch.category());
            ItemOutcome outcome = switch (detailFetch.category()) {
                case CHALLENGE_PAGE, INVALID_CONTENT_TYPE, EMPTY_BODY -> ItemOutcome.STRUCTURE_FAILURE;
                default -> ItemOutcome.FETCH_FAILURE;
            };
            return new ItemProcessingResult(outcome,
                    "externalId=" + item.externalId() + ", category=" + detailFetch.category(),
                    false);
        }
        stats.fetchSucceeded++;
        ParsedContent parsed;
        try {
            parsed = adapter.parseDetail(contentType, detailFetch.body(), detailFetch.finalUrl());
        } catch (RuntimeException parseFailure) {
            stats.failed++;
            sourceItemService.recordParseFailure(adapter.sourceCode(), contentType,
                    item.externalId(), parseFailure.getClass().getSimpleName());
            return new ItemProcessingResult(ItemOutcome.STRUCTURE_FAILURE,
                    "externalId=" + item.externalId() + ", parser="
                            + parseFailure.getClass().getSimpleName(), false);
        }
        if (!parsed.valid()) {
            stats.failed++;
            sourceItemService.recordParseFailure(adapter.sourceCode(), contentType,
                    item.externalId(), "MISSING_REQUIRED_FIELDS");
            String diagnostic = "externalId=" + item.externalId() + ", missing="
                    + parsed.diagnostics().missingRequiredFields() + ", fingerprint="
                    + parsed.diagnostics().pageFingerprint();
            log.atWarn().log("Detail parse rejected: source={}, {}", adapter.sourceCode(), diagnostic);
            return new ItemProcessingResult(ItemOutcome.STRUCTURE_FAILURE, diagnostic, false);
        }
        stats.parseSucceeded++;
        String detailFingerprint = SourceFingerprint.forDetail(parsed);
        boolean detailUnchanged = detailFingerprint.equals(observation.previousDetailFingerprint());
        if (detailUnchanged && "filtered".equals(observation.previousParseStatus())) {
            sourceItemService.recordFiltered(adapter.sourceCode(), contentType,
                    item.externalId(), detailFingerprint);
            stats.filtered++;
            return new ItemProcessingResult(ItemOutcome.FILTERED, "detail-unchanged", true);
        }
        if (detailUnchanged && "parsed".equals(observation.previousParseStatus())
                && observation.internalContentId() != null) {
            sourceItemService.recordParsed(adapter.sourceCode(), contentType, item.externalId(),
                    observation.internalContentId(), detailFingerprint);
            stats.unchanged++;
            return new ItemProcessingResult(ItemOutcome.UNCHANGED, "detail-unchanged", true);
        }
        if (!matchesGenreFilter(parsed.genres(), genreFilter)) {
            sourceItemService.recordFiltered(adapter.sourceCode(), contentType,
                    item.externalId(), detailFingerprint);
            stats.filtered++;
            return new ItemProcessingResult(ItemOutcome.FILTERED, "filtered", detailUnchanged);
        }
        try {
            CrawlerContentPersistence.PersistResult persisted = contentPersistence.persist(
                    adapter.sourceCode(), parsed);
            long internalContentId = persisted.contentId() > 0
                    ? persisted.contentId() : Long.parseLong(parsed.externalId());
            sourceItemService.recordParsed(adapter.sourceCode(), contentType, item.externalId(),
                    internalContentId, detailFingerprint);
            if (persisted.added()) stats.added++;
            if (persisted.updated()) stats.updated++;
            if (persisted.unchanged()) stats.unchanged++;
            return new ItemProcessingResult(ItemOutcome.SUCCESS, "ok", persisted.unchanged());
        } catch (RuntimeException persistenceFailure) {
            stats.failed++;
            sourceItemService.recordPersistFailure(adapter.sourceCode(), contentType,
                    item.externalId(), detailFingerprint,
                    persistenceFailure.getClass().getSimpleName());
            log.warn("Detail persistence failed: source={}, externalId={}, error={}",
                    adapter.sourceCode(), item.externalId(),
                    persistenceFailure.getClass().getSimpleName());
            return new ItemProcessingResult(ItemOutcome.PERSISTENCE_FAILURE,
                    "externalId=" + item.externalId() + ", persistence="
                            + persistenceFailure.getClass().getSimpleName(), false);
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
        UNCHANGED,
        FILTERED,
        FETCH_FAILURE,
        STRUCTURE_FAILURE,
        PERSISTENCE_FAILURE,
        CANCELLED
    }

    private record ItemProcessingResult(ItemOutcome outcome, String diagnostic,
                                        boolean oldItem) {
    }
}
