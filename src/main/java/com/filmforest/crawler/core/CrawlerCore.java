package com.filmforest.crawler.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.filmforest.common.type.ContentType;
import com.filmforest.crawler.config.CrawlerExecutionProperties;
import com.filmforest.crawler.entity.CrawlerCrawlMode;
import com.filmforest.crawler.entity.CrawlerFailureStage;
import com.filmforest.crawler.entity.CrawlerSchedule;
import com.filmforest.crawler.entity.CrawlerTaskLog;
import com.filmforest.crawler.http.FetchCategory;
import com.filmforest.crawler.http.FetchResult;
import com.filmforest.crawler.http.HttpFetcher;
import com.filmforest.crawler.mapper.CrawlerTaskLogMapper;
import com.filmforest.crawler.model.CrawlerCheckpoint;
import com.filmforest.crawler.model.ParsedContent;
import com.filmforest.crawler.model.SourceListItem;
import com.filmforest.crawler.service.CrawlExecutionSummary;
import com.filmforest.crawler.service.CrawlerScheduleService;
import com.filmforest.crawler.service.CrawlerGenreService;
import com.filmforest.crawler.service.CrawlerItemFailureService;
import com.filmforest.crawler.service.CrawlerSourceItemService;
import com.filmforest.crawler.service.CrawlerTime;
import com.filmforest.crawler.service.SourceFingerprint;
import com.filmforest.crawler.source.CrawlerSourceAdapter;
import com.filmforest.crawler.source.CrawlerResourceEnricher;
import com.filmforest.crawler.source.SourceAdapterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
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
    private final CrawlerGenreService genreService;
    private final CrawlerSourceItemService sourceItemService;
    private final CrawlerItemFailureService itemFailureService;
    private final CrawlerExecutionProperties executionProperties;
    private final ObjectMapper objectMapper;
    private final ThreadLocal<Long> executingJobId = new ThreadLocal<>();

    public CrawlerCore(CrawlerScheduleService scheduleService,
                       CrawlerTaskLogMapper taskLogMapper,
                       SourceAdapterRegistry sourceAdapterRegistry,
                       HttpFetcher httpFetcher,
                       CrawlerContentPersistence contentPersistence,
                       CrawlerGenreService genreService,
                       CrawlerSourceItemService sourceItemService,
                       CrawlerItemFailureService itemFailureService,
                       CrawlerExecutionProperties executionProperties,
                       ObjectMapper objectMapper) {
        this.scheduleService = scheduleService;
        this.taskLogMapper = taskLogMapper;
        this.sourceAdapterRegistry = sourceAdapterRegistry;
        this.httpFetcher = httpFetcher;
        this.contentPersistence = contentPersistence;
        this.genreService = genreService;
        this.sourceItemService = sourceItemService;
        this.itemFailureService = itemFailureService;
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
        CrawlerSourceAdapter adapter = sourceAdapterRegistry.require(
                schedule.getAdapterCode() == null ? schedule.getSourceSite() : schedule.getAdapterCode());
        CrawlerCrawlMode crawlMode = CrawlerCrawlMode.fromCode(job.getCrawlMode() == null
                ? schedule.getCrawlMode() : job.getCrawlMode());
        CrawlerCheckpoint checkpoint = crawlMode == CrawlerCrawlMode.FULL
                ? readCheckpoint(job) : CrawlerCheckpoint.atPage(1);
        int startPage = checkpoint.nextPage();
        int maxItems = crawlMode == CrawlerCrawlMode.FULL ? Integer.MAX_VALUE
                : schedule.getBatchSize() == null ? 20 : Math.max(1, schedule.getBatchSize());
        int rateLimitMs = schedule.getRateLimitMs() == null
                ? 0 : Math.max(0, schedule.getRateLimitMs());
        Set<String> genreFilter = parseGenreFilter(schedule.getGenreFilter());

        executingJobId.set(logId);
        try {
            return crawl(scheduleId, adapter, contentType, crawlMode, startPage, maxItems,
                    rateLimitMs, genreFilter, checkpoint, cancellation);
        } finally {
            executingJobId.remove();
        }
    }

    private CrawlExecutionSummary crawl(Long scheduleId, CrawlerSourceAdapter adapter,
                                        ContentType contentType, CrawlerCrawlMode crawlMode,
                                        int startPage, int maxItems, int rateLimitMs,
                                        Set<String> genreFilter, CrawlerCheckpoint resumeCheckpoint,
                                        AtomicBoolean cancellation) {
        MutableStats stats = new MutableStats();
        int page = startPage;
        int consecutiveStructureFailures = 0;
        int consecutiveOldItems = 0;
        int latestStopThreshold = Math.max(1,
                executionProperties.getLatestConsecutiveUnchanged());
        int latestRecentPages = Math.max(1, executionProperties.getLatestRecentPages());
        boolean latestBoundaryReached = false;
        CrawlerCheckpoint checkpoint = resumeCheckpoint;
        String lastCommittedExternalId = checkpoint.lastCommittedExternalId();
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
            int firstItemIndex = page == checkpoint.nextPage()
                    ? checkpoint.resumeItemIndex(items) : 0;
            for (int itemIndex = firstItemIndex; itemIndex < items.size(); itemIndex++) {
                SourceListItem item = items.get(itemIndex);
                if (stats.discovered >= maxItems || isCancellationRequested(cancellation)) {
                    pageCompleted = false;
                    break;
                }
                CrawlerSourceItemService.Observation observation = sourceItemService.observeListItem(
                        adapter.sourceCode(), contentType, item);
                stats.discovered++;
                CrawlerCheckpoint beforeItem = CrawlerCheckpoint.beforeItem(page, itemIndex,
                        item.externalId(), lastCommittedExternalId);
                recordProgress(beforeItem, item.sourceUrl(), stats);
                ItemProcessingResult result = processItem(adapter, contentType, item, rateLimitMs,
                        genreFilter, cancellation, stats, observation,
                        crawlMode == CrawlerCrawlMode.LATEST && page > latestRecentPages);
                if (result.outcome() == ItemOutcome.STRUCTURE_FAILURE) {
                    consecutiveStructureFailures++;
                    if (consecutiveStructureFailures >= STRUCTURE_FAILURE_THRESHOLD) {
                        recordProgress(beforeItem, item.sourceUrl(), stats);
                        throw new CrawlerSourceStructureException(adapter.sourceCode(),
                                consecutiveStructureFailures, result.diagnostic());
                    }
                } else if (result.outcome() == ItemOutcome.SUCCESS
                        || result.outcome() == ItemOutcome.UNCHANGED
                        || result.outcome() == ItemOutcome.FILTERED) {
                    consecutiveStructureFailures = 0;
                }
                if (result.outcome() == ItemOutcome.CANCELLED) {
                    pageCompleted = false;
                    checkpoint = beforeItem;
                    recordProgress(checkpoint, item.sourceUrl(), stats);
                    break;
                }
                if (isCommitted(result.outcome())) {
                    lastCommittedExternalId = item.externalId();
                }
                checkpoint = checkpointAfter(items, page, itemIndex, lastCommittedExternalId);
                consecutiveOldItems = result.oldItem() ? consecutiveOldItems + 1 : 0;
                recordProgress(checkpoint, nextItemUrl(items, itemIndex), stats);
                if (crawlMode == CrawlerCrawlMode.LATEST && page >= latestRecentPages
                        && consecutiveOldItems >= latestStopThreshold) {
                    pageCompleted = false;
                    latestBoundaryReached = true;
                    break;
                }
            }
            if (pageCompleted) {
                checkpoint = new CrawlerCheckpoint(CrawlerCheckpoint.CURRENT_VERSION,
                        page + 1, 0, null, lastCommittedExternalId);
                recordProgress(checkpoint, null, stats);
            }
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
            recordProgress(checkpoint, null, stats);
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
            String diagnostic = "externalId=" + item.externalId()
                    + ", category=" + detailFetch.category();
            recordItemFailure(adapter, contentType, item, CrawlerFailureStage.FETCH,
                    detailFetch.category().name(), detailFetch.attemptCount(),
                    detailFetch.retryable(), diagnostic);
            return new ItemProcessingResult(outcome, diagnostic, false);
        }
        stats.fetchSucceeded++;
        ParsedContent parsed;
        try {
            parsed = adapter.parseDetail(contentType, detailFetch.body(), detailFetch.finalUrl());
            if (adapter instanceof CrawlerResourceEnricher enricher) {
                parsed = enricher.enrichResources(parsed, httpFetcher, rateLimitMs, cancellation);
            }
        } catch (RuntimeException parseFailure) {
            stats.failed++;
            sourceItemService.recordParseFailure(adapter.sourceCode(), contentType,
                    item.externalId(), parseFailure.getClass().getSimpleName());
            String diagnostic = "externalId=" + item.externalId() + ", parser="
                    + parseFailure.getClass().getSimpleName();
            recordItemFailure(adapter, contentType, item, CrawlerFailureStage.PARSE,
                    parseFailure.getClass().getSimpleName(), 1, false, diagnostic);
            return new ItemProcessingResult(ItemOutcome.STRUCTURE_FAILURE, diagnostic, false);
        }
        if (!parsed.valid()) {
            stats.failed++;
            sourceItemService.recordParseFailure(adapter.sourceCode(), contentType,
                    item.externalId(), "MISSING_REQUIRED_FIELDS");
            String diagnostic = "externalId=" + item.externalId() + ", missing="
                    + parsed.diagnostics().missingRequiredFields() + ", fingerprint="
                    + parsed.diagnostics().pageFingerprint();
            log.atWarn().log("Detail parse rejected: source={}, {}", adapter.sourceCode(), diagnostic);
            recordItemFailure(adapter, contentType, item, CrawlerFailureStage.PARSE,
                    "MISSING_REQUIRED_FIELDS", 1, false, diagnostic);
            return new ItemProcessingResult(ItemOutcome.STRUCTURE_FAILURE, diagnostic, false);
        }
        stats.parseSucceeded++;
        CrawlerGenreService.ResolvedGenres resolvedGenres = genreService.resolve(
                adapter.sourceCode(), contentType, parsed.genres());
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
                    observation.internalContentId(),
                    SourceFingerprint.forCanonicalContent(contentType, parsed.title(), parsed.year()),
                    detailFingerprint);
            stats.unchanged++;
            return new ItemProcessingResult(ItemOutcome.UNCHANGED, "detail-unchanged", true);
        }
        if (!matchesGenreFilter(resolvedGenres.names(), genreFilter)) {
            sourceItemService.recordFiltered(adapter.sourceCode(), contentType,
                    item.externalId(), detailFingerprint);
            stats.filtered++;
            return new ItemProcessingResult(ItemOutcome.FILTERED, "filtered", detailUnchanged);
        }
        int maxPersistenceAttempts = Math.min(5, Math.max(1,
                executionProperties.getItemPersistenceMaxAttempts()));
        for (int attempt = 1; attempt <= maxPersistenceAttempts; attempt++) {
            try {
                CrawlerContentPersistence.PersistResult persisted = contentPersistence.persist(
                        adapter.sourceCode(), parsed, resolvedGenres, observation.internalContentId());
                long internalContentId = persisted.contentId() > 0
                        ? persisted.contentId() : Long.parseLong(parsed.externalId());
                String canonicalKey = persisted.canonicalKey() == null
                        ? SourceFingerprint.forCanonicalContent(
                                contentType, parsed.title(), parsed.year())
                        : persisted.canonicalKey();
                sourceItemService.recordParsed(adapter.sourceCode(), contentType, item.externalId(),
                        internalContentId, canonicalKey, detailFingerprint);
                if (persisted.added()) stats.added++;
                if (persisted.updated()) stats.updated++;
                if (persisted.unchanged()) stats.unchanged++;
                return new ItemProcessingResult(ItemOutcome.SUCCESS, "ok", persisted.unchanged());
            } catch (RuntimeException persistenceFailure) {
                boolean retryable = isRetryablePersistenceFailure(persistenceFailure);
                boolean exhausted = attempt >= maxPersistenceAttempts;
                if (retryable && !exhausted) {
                    long delayMs = persistenceRetryDelayMs(attempt);
                    log.warn("Retrying transient persistence failure: source={}, externalId={}, attempt={}",
                            adapter.sourceCode(), item.externalId(), attempt);
                    if (!sleepWithCancellation(delayMs, cancellation)) {
                        return new ItemProcessingResult(ItemOutcome.CANCELLED, "cancelled", false);
                    }
                    continue;
                }
                stats.failed++;
                String category = persistenceFailure.getClass().getSimpleName();
                String diagnostic = "externalId=" + item.externalId()
                        + ", persistence=" + category;
                sourceItemService.recordPersistFailure(adapter.sourceCode(), contentType,
                        item.externalId(), detailFingerprint, category);
                recordItemFailure(adapter, contentType, item, CrawlerFailureStage.PERSISTENCE,
                        category, attempt, retryable && exhausted, diagnostic);
                log.warn("Detail persistence failed: source={}, externalId={}, error={}, attempts={}",
                        adapter.sourceCode(), item.externalId(), category, attempt);
                return new ItemProcessingResult(ItemOutcome.PERSISTENCE_FAILURE,
                        diagnostic, false);
            }
        }
        throw new IllegalStateException("Unreachable persistence retry state");
    }

    private void recordItemFailure(CrawlerSourceAdapter adapter, ContentType contentType,
                                   SourceListItem item, CrawlerFailureStage stage,
                                   String errorCategory, int attempts, boolean retryExhausted,
                                   String diagnostic) {
        Long jobId = executingJobId.get();
        if (jobId == null) return;
        try {
            itemFailureService.record(jobId, adapter.sourceCode(), contentType, item, stage,
                    errorCategory, attempts, retryExhausted, diagnostic);
        } catch (RuntimeException recordFailure) {
            log.warn("Failed to record crawler item failure: jobId={}, source={}, externalId={}, error={}",
                    jobId, adapter.sourceCode(), item.externalId(),
                    recordFailure.getClass().getSimpleName());
        }
    }

    private long persistenceRetryDelayMs(int attempt) {
        long base = Math.min(5_000L,
                Math.max(0L, executionProperties.getItemRetryBaseDelayMs()));
        return Math.min(base * (1L << Math.min(Math.max(0, attempt - 1), 6)), 5_000L);
    }

    private static boolean isRetryablePersistenceFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof TransientDataAccessException
                    || current instanceof RecoverableDataAccessException
                    || current instanceof SQLTransientException
                    || current instanceof SQLRecoverableException) {
                return true;
            }
            if (current == current.getCause()) break;
            current = current.getCause();
        }
        return false;
    }

    private void recordProgress(CrawlerCheckpoint checkpoint, String currentItem, MutableStats stats) {
        Long jobId = executingJobId.get();
        if (jobId == null) return;
        try {
            String checkpointJson = objectMapper.writeValueAsString(checkpoint);
            taskLogMapper.updateProgress(jobId, checkpoint.nextPage(), currentItem,
                    stats.discovered, stats.fetchSucceeded, stats.parseSucceeded,
                    stats.added, stats.updated, stats.unchanged, stats.filtered, stats.failed,
                    checkpointJson, CrawlerTime.nowUtc());
        } catch (Exception error) {
            log.warn("Failed to update crawler progress: jobId={}, error={}",
                    jobId, error.getClass().getSimpleName());
        }
    }

    private CrawlerCheckpoint readCheckpoint(CrawlerTaskLog job) {
        int fallbackPage = job.getCurrentPage() == null ? 1 : Math.max(1, job.getCurrentPage());
        if (job.getCheckpoint() == null || job.getCheckpoint().isBlank()) {
            return CrawlerCheckpoint.atPage(fallbackPage);
        }
        try {
            return objectMapper.readValue(job.getCheckpoint(), CrawlerCheckpoint.class)
                    .normalized(fallbackPage);
        } catch (Exception invalidCheckpoint) {
            log.warn("Ignoring invalid crawler checkpoint: jobId={}, error={}",
                    job.getId(), invalidCheckpoint.getClass().getSimpleName());
            return CrawlerCheckpoint.atPage(fallbackPage);
        }
    }

    private static CrawlerCheckpoint checkpointAfter(List<SourceListItem> items, int page,
                                                      int itemIndex, String lastCommittedExternalId) {
        int nextIndex = itemIndex + 1;
        if (nextIndex < items.size()) {
            return new CrawlerCheckpoint(CrawlerCheckpoint.CURRENT_VERSION, page, nextIndex,
                    items.get(nextIndex).externalId(), lastCommittedExternalId);
        }
        return new CrawlerCheckpoint(CrawlerCheckpoint.CURRENT_VERSION, page + 1, 0,
                null, lastCommittedExternalId);
    }

    private static String nextItemUrl(List<SourceListItem> items, int itemIndex) {
        int nextIndex = itemIndex + 1;
        return nextIndex < items.size() ? items.get(nextIndex).sourceUrl() : null;
    }

    private static boolean isCommitted(ItemOutcome outcome) {
        return outcome == ItemOutcome.SUCCESS || outcome == ItemOutcome.UNCHANGED
                || outcome == ItemOutcome.FILTERED;
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
