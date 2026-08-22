package com.filmforest.crawler.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.filmforest.common.type.ContentType;
import com.filmforest.crawler.config.CrawlerExecutionProperties;
import com.filmforest.crawler.entity.CrawlerCrawlMode;
import com.filmforest.crawler.entity.CrawlerCursorState;
import com.filmforest.crawler.entity.CrawlerEndPolicy;
import com.filmforest.crawler.entity.CrawlerFailureStage;
import com.filmforest.crawler.entity.CrawlerSchedule;
import com.filmforest.crawler.entity.CrawlerScheduleCursor;
import com.filmforest.crawler.entity.CrawlerSourceSort;
import com.filmforest.crawler.entity.CrawlerTaskLog;
import com.filmforest.crawler.entity.CrawlerTraversalMode;
import com.filmforest.crawler.http.FetchCategory;
import com.filmforest.crawler.http.FetchResult;
import com.filmforest.crawler.http.HttpFetcher;
import com.filmforest.crawler.mapper.CrawlerTaskLogMapper;
import com.filmforest.crawler.model.CrawlerCheckpoint;
import com.filmforest.crawler.model.CrawlerSourceQuery;
import com.filmforest.crawler.model.ParsedContent;
import com.filmforest.crawler.model.SourceListItem;
import com.filmforest.crawler.service.CrawlExecutionSummary;
import com.filmforest.crawler.service.CrawlerScheduleService;
import com.filmforest.crawler.service.CrawlerGenreService;
import com.filmforest.crawler.service.CrawlerItemFailureService;
import com.filmforest.crawler.service.CrawlerItemSuccessService;
import com.filmforest.crawler.service.CrawlerQueryProfile;
import com.filmforest.crawler.service.CrawlerRecoveryRequiredException;
import com.filmforest.crawler.service.CrawlerScheduleCursorService;
import com.filmforest.crawler.service.CrawlerSourceItemService;
import com.filmforest.crawler.service.CrawlerTime;
import com.filmforest.crawler.service.SourceFingerprint;
import com.filmforest.crawler.source.CrawlerSourceAdapter;
import com.filmforest.crawler.source.CrawlerResourceEnricher;
import com.filmforest.crawler.source.SourceAdapterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final CrawlerItemSuccessService itemSuccessService;
    private final CrawlerExecutionProperties executionProperties;
    private final ObjectMapper objectMapper;
    private final CrawlerScheduleCursorService cursorService;
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
        this(scheduleService, taskLogMapper, sourceAdapterRegistry, httpFetcher, contentPersistence,
                genreService, sourceItemService, itemFailureService, executionProperties,
                objectMapper, null, null);
    }

    public CrawlerCore(CrawlerScheduleService scheduleService,
                       CrawlerTaskLogMapper taskLogMapper,
                       SourceAdapterRegistry sourceAdapterRegistry,
                       HttpFetcher httpFetcher,
                       CrawlerContentPersistence contentPersistence,
                       CrawlerGenreService genreService,
                       CrawlerSourceItemService sourceItemService,
                       CrawlerItemFailureService itemFailureService,
                       CrawlerExecutionProperties executionProperties,
                       ObjectMapper objectMapper,
                       CrawlerScheduleCursorService cursorService) {
        this(scheduleService, taskLogMapper, sourceAdapterRegistry, httpFetcher, contentPersistence,
                genreService, sourceItemService, itemFailureService, executionProperties,
                objectMapper, null, cursorService);
    }

    @Autowired
    public CrawlerCore(CrawlerScheduleService scheduleService,
                       CrawlerTaskLogMapper taskLogMapper,
                       SourceAdapterRegistry sourceAdapterRegistry,
                       HttpFetcher httpFetcher,
                       CrawlerContentPersistence contentPersistence,
                       CrawlerGenreService genreService,
                       CrawlerSourceItemService sourceItemService,
                       CrawlerItemFailureService itemFailureService,
                       CrawlerExecutionProperties executionProperties,
                       ObjectMapper objectMapper,
                       CrawlerItemSuccessService itemSuccessService,
                       CrawlerScheduleCursorService cursorService) {
        this.scheduleService = scheduleService;
        this.taskLogMapper = taskLogMapper;
        this.sourceAdapterRegistry = sourceAdapterRegistry;
        this.httpFetcher = httpFetcher;
        this.contentPersistence = contentPersistence;
        this.genreService = genreService;
        this.sourceItemService = sourceItemService;
        this.itemFailureService = itemFailureService;
        this.itemSuccessService = itemSuccessService;
        this.executionProperties = executionProperties;
        this.objectMapper = objectMapper;
        this.cursorService = cursorService;
    }

    public CrawlExecutionSummary executeCrawl(Long scheduleId, Long logId,
                                              AtomicBoolean cancellation) {
        CrawlerSchedule schedule = scheduleService.getSchedule(scheduleId);
        CrawlerTaskLog job = taskLogMapper.selectById(logId);
        if (schedule == null || job == null) {
            throw new IllegalArgumentException("Crawler schedule or job does not exist");
        }
        ContentType contentType = parseContentType(job.getContentType() == null
                ? schedule.getContentType() : job.getContentType());
        if (isCancellationRequested(cancellation)) {
            return emptySummary();
        }
        String sourceCode = job.getSourceCode() == null
                ? (schedule.getAdapterCode() == null ? schedule.getSourceSite() : schedule.getAdapterCode())
                : job.getSourceCode();
        CrawlerSourceAdapter adapter = sourceAdapterRegistry.require(sourceCode);
        CrawlerCrawlMode crawlMode = CrawlerCrawlMode.fromCode(job.getCrawlMode() == null
                ? schedule.getCrawlMode() : job.getCrawlMode());
        CrawlerSourceSort sourceSort = CrawlerSourceSort.fromCode(job.getSourceSort() == null
                ? (schedule.getSourceSort() == null ? schedule.getPriority() : schedule.getSourceSort())
                : job.getSourceSort());
        CrawlerTraversalMode traversalMode = traversalMode(job, schedule, crawlMode, sourceSort);
        CrawlerEndPolicy endPolicy = endPolicy(schedule);
        CrawlerScheduleCursor cursor = cursorService == null
                ? null : cursorService.prepare(schedule, job);
        if (cursor != null) {
            CrawlerCursorState cursorState = cursorState(cursor.getState());
            if (cursorState == CrawlerCursorState.INVALIDATED
                    || cursorState == CrawlerCursorState.RECOVERY_REQUIRED) {
                throw new CrawlerRecoveryRequiredException(
                        cursor.getLastError() == null ? "游标需要人工恢复" : cursor.getLastError());
            }
            if (cursorState == CrawlerCursorState.SOURCE_UNAVAILABLE) {
                throw new CrawlerSourceUnavailableException(
                        cursor.getLastError() == null ? "来源当前不可用" : cursor.getLastError());
            }
        }
        CrawlerCheckpoint checkpoint = cursor != null
                ? checkpointFromCursor(cursor)
                : crawlMode == CrawlerCrawlMode.FULL ? readCheckpoint(job) : CrawlerCheckpoint.atPage(1);
        int newItemLimit = positiveOrDefault(schedule.getNewItemLimit(),
                schedule.getBatchSize() == null ? 10 : schedule.getBatchSize());
        int backfillItemLimit = positiveOrDefault(schedule.getBackfillItemLimit(),
                schedule.getBatchSize() == null ? 10 : schedule.getBatchSize());
        int manualRunLimit = positiveOrDefault(schedule.getManualRunLimit(),
                schedule.getBatchSize() == null ? 100 : schedule.getBatchSize());
        int legacyMaxItems = crawlMode == CrawlerCrawlMode.FULL ? Integer.MAX_VALUE
                : schedule.getBatchSize() == null ? 20 : Math.max(1, schedule.getBatchSize());
        int rateLimitMs = schedule.getRateLimitMs() == null
                ? 2000 : Math.max(2000, schedule.getRateLimitMs());
        Set<String> genreFilter = parseGenreFilter(schedule.getGenreFilter());
        Map<String, String> sourceFilters = cursor == null
                ? sourceFilters(schedule)
                : CrawlerQueryProfile.parseFilterSnapshot(job.getSourceFilterSnapshot());

        executingJobId.set(logId);
        try {
            return crawl(scheduleId, adapter, contentType, crawlMode, sourceSort, traversalMode,
                    endPolicy, legacyMaxItems, newItemLimit, backfillItemLimit, manualRunLimit,
                    rateLimitMs, genreFilter, sourceFilters, checkpoint, cursor, cancellation);
        } finally {
            executingJobId.remove();
        }
    }

    private CrawlExecutionSummary crawl(Long scheduleId, CrawlerSourceAdapter adapter,
                                        ContentType contentType, CrawlerCrawlMode crawlMode,
                                        CrawlerSourceSort sourceSort, CrawlerTraversalMode traversalMode,
                                        CrawlerEndPolicy endPolicy, int legacyMaxItems,
                                        int newItemLimit, int backfillItemLimit, int manualRunLimit,
                                        int rateLimitMs, Set<String> genreFilter,
                                        Map<String, String> sourceFilters,
                                        CrawlerCheckpoint resumeCheckpoint,
                                        CrawlerScheduleCursor cursor,
                                        AtomicBoolean cancellation) {
        MutableStats stats = new MutableStats();
        int page = resumeCheckpoint.nextPage();
        int consecutiveStructureFailures = 0;
        int consecutiveOldItems = 0;
        int latestStopThreshold = Math.max(1,
                executionProperties.getLatestConsecutiveUnchanged());
        int latestRecentPages = Math.max(1, executionProperties.getLatestRecentPages());
        boolean latestBoundaryReached = false;
        CrawlerCheckpoint checkpoint = resumeCheckpoint;
        String lastCommittedExternalId = checkpoint.lastCommittedExternalId();
        boolean restartedCycle = false;
        while (canContinue(stats, crawlMode, traversalMode, legacyMaxItems,
                newItemLimit, backfillItemLimit, manualRunLimit, cursor != null)
                && !isCancellationRequested(cancellation)) {
            URI listUri = cursor == null
                    ? adapter.listUri(contentType, page)
                    : adapter.listUri(new CrawlerSourceQuery(contentType, sourceSort, sourceFilters, page));
            if (listUri == null) {
                throw new IllegalStateException("来源未生成有效列表 URL: " + adapter.sourceCode());
            }
            FetchResult listFetch = httpFetcher.fetch(listUri, Map.of(), rateLimitMs, cancellation);
            if (listFetch.category() == FetchCategory.CANCELLED) {
                break;
            }
            if (!listFetch.successful()) {
                markCursorUnavailable(cursor, listFetch);
                throw new CrawlerFetchException("List fetch failed", listFetch);
            }
            List<SourceListItem> items;
            try {
                items = adapter.parseList(listFetch.body(), listFetch.finalUrl());
            } catch (RuntimeException parseFailure) {
                if (cursor != null) {
                    cursorService.mark(cursor, CrawlerCursorState.RECOVERY_REQUIRED,
                            "列表结构无法解析：" + parseFailure.getClass().getSimpleName());
                }
                throw new CrawlerSourceStructureException(adapter.sourceCode(),
                        STRUCTURE_FAILURE_THRESHOLD, parseFailure.getMessage());
            }
            stats.pagesScanned++;
            stats.listItemsScanned += items.size();
            if (cursor != null && anchorMissing(checkpoint, items)) {
                NearbyPage recovered = recoverNearbyPage(adapter, contentType, sourceSort,
                        sourceFilters, page, checkpoint, rateLimitMs, cancellation, stats, cursor);
                page = recovered.page();
                items = recovered.items();
                checkpoint = new CrawlerCheckpoint(CrawlerCheckpoint.CURRENT_VERSION, page, 0,
                        checkpoint.nextExternalId(), checkpoint.lastCommittedExternalId());
                log.info("恢复分页锚点: jobId={}, scheduleId={}, page={}, anchor={}",
                        executingJobId.get(), scheduleId, page, anchorOf(checkpoint));
            }
            if (items.isEmpty()) {
                if (cursor != null && shouldHoldAtEnd(sourceSort, endPolicy)) {
                    cursorService.mark(cursor, CrawlerCursorState.COMPLETE, null);
                } else if (cursor != null && !restartedCycle
                        && CrawlerEndPolicy.RESTART_CYCLE == endPolicy) {
                    restartedCycle = true;
                    page = 1;
                    checkpoint = CrawlerCheckpoint.atPage(1);
                    lastCommittedExternalId = null;
                    cursorService.advance(cursor, null, null, 1, 0,
                            null, CrawlerCursorState.ACTIVE.getCode(), "开始新的来源遍历周期");
                    continue;
                }
                break;
            }

            boolean pageCompleted = true;
            int firstItemIndex = page == checkpoint.nextPage()
                    ? checkpoint.resumeItemIndex(items) : 0;
            for (int itemIndex = firstItemIndex; itemIndex < items.size(); itemIndex++) {
                SourceListItem item = items.get(itemIndex);
                if (!canProcessNextItem(stats, crawlMode, traversalMode, legacyMaxItems,
                        newItemLimit, backfillItemLimit, manualRunLimit, cursor != null)
                        || isCancellationRequested(cancellation)) {
                    pageCompleted = false;
                    break;
                }
                CrawlerSourceItemService.Observation observation = sourceItemService.observeListItem(
                        adapter.sourceCode(), contentType, item);
                stats.discovered++;
                CrawlerCheckpoint beforeItem = CrawlerCheckpoint.beforeItem(page, itemIndex,
                        item.externalId(), lastCommittedExternalId);
                recordProgress(beforeItem, item.sourceUrl(), stats, cursor, false);
                ItemProcessingResult result = processItem(adapter, contentType, item, rateLimitMs,
                        genreFilter, cancellation, stats, observation,
                        crawlMode == CrawlerCrawlMode.LATEST && page > latestRecentPages);
                if (result.outcome() == ItemOutcome.STRUCTURE_FAILURE) {
                    consecutiveStructureFailures++;
                    if (consecutiveStructureFailures >= STRUCTURE_FAILURE_THRESHOLD) {
                        recordProgress(beforeItem, item.sourceUrl(), stats, cursor, false);
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
                    recordProgress(checkpoint, item.sourceUrl(), stats, cursor, false);
                    break;
                }
                if (isCommitted(result.outcome())) {
                    lastCommittedExternalId = item.externalId();
                }
                checkpoint = checkpointAfter(items, page, itemIndex, lastCommittedExternalId);
                consecutiveOldItems = result.oldItem() ? consecutiveOldItems + 1 : 0;
                boolean backfill = isBackfillItem(crawlMode, traversalMode, page, latestRecentPages,
                        result.oldItem());
                if (backfill) stats.backfillItems++; else stats.newItems++;
                recordProgress(checkpoint, nextItemUrl(items, itemIndex), stats, cursor, true);
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
                recordProgress(checkpoint, null, stats, cursor, false);
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
            recordProgress(checkpoint, null, stats, cursor, false);
        }
        return stats.toSummary();
    }

    private NearbyPage recoverNearbyPage(CrawlerSourceAdapter adapter, ContentType contentType,
                                         CrawlerSourceSort sourceSort, Map<String, String> sourceFilters,
                                         int currentPage, CrawlerCheckpoint checkpoint,
                                         int rateLimitMs, AtomicBoolean cancellation,
                                         MutableStats stats, CrawlerScheduleCursor cursor) {
        for (int distance = 1; distance <= 2; distance++) {
            int before = currentPage - distance;
            NearbyPage result = before < 1 ? null : fetchNearbyPage(adapter, contentType, sourceSort,
                    sourceFilters, before, checkpoint, rateLimitMs, cancellation, stats, cursor);
            if (result != null) return result;
            int after = currentPage + distance;
            result = fetchNearbyPage(adapter, contentType, sourceSort, sourceFilters, after,
                    checkpoint, rateLimitMs, cancellation, stats, cursor);
            if (result != null) return result;
        }
        cursorService.mark(cursor, CrawlerCursorState.RECOVERY_REQUIRED,
                "分页锚点在当前页前后 2 页内均未找到，需人工确认后重置游标");
        throw new CrawlerRecoveryRequiredException(
                "分页发生漂移且无法在前后 2 页恢复锚点：" + anchorOf(checkpoint));
    }

    private NearbyPage fetchNearbyPage(CrawlerSourceAdapter adapter, ContentType contentType,
                                       CrawlerSourceSort sourceSort, Map<String, String> sourceFilters,
                                       int page, CrawlerCheckpoint checkpoint, int rateLimitMs,
                                       AtomicBoolean cancellation, MutableStats stats,
                                       CrawlerScheduleCursor cursor) {
        URI uri = adapter.listUri(new CrawlerSourceQuery(contentType, sourceSort, sourceFilters, page));
        FetchResult fetched = httpFetcher.fetch(uri, Map.of(), rateLimitMs, cancellation);
        if (!fetched.successful()) {
            markCursorUnavailable(cursor, fetched);
            throw new CrawlerFetchException("恢复分页时列表读取失败", fetched);
        }
        List<SourceListItem> items;
        try {
            items = adapter.parseList(fetched.body(), fetched.finalUrl());
        } catch (RuntimeException parseFailure) {
            cursorService.mark(cursor, CrawlerCursorState.RECOVERY_REQUIRED,
                    "恢复分页时列表结构无法解析");
            throw new CrawlerSourceStructureException(adapter.sourceCode(),
                    STRUCTURE_FAILURE_THRESHOLD, parseFailure.getMessage());
        }
        stats.pagesScanned++;
        stats.listItemsScanned += items.size();
        return containsAnchor(checkpoint, items) ? new NearbyPage(page, items) : null;
    }

    /**
     * 只有在页内续爬时才需要校验锚点。
     *
     * 页处理完成后的检查点形如「nextPage=N+1、nextItemIndex=0、
     * nextExternalId=null、lastCommittedExternalId=上一页最后一项」。
     * 上一页最后一项不属于下一页，不能把它当作下一页的必需锚点，否则每次
     * 翻页都会回到上一页恢复，形成无限恢复循环。
     */
    static boolean anchorMissing(CrawlerCheckpoint checkpoint, List<SourceListItem> items) {
        if (checkpoint.nextExternalId() != null) {
            return items.stream().noneMatch(item -> checkpoint.nextExternalId().equals(item.externalId()));
        }
        return checkpoint.nextItemIndex() > 0
                && checkpoint.lastCommittedExternalId() != null
                && items.stream().noneMatch(item -> checkpoint.lastCommittedExternalId().equals(item.externalId()));
    }

    private static boolean containsAnchor(CrawlerCheckpoint checkpoint, List<SourceListItem> items) {
        if (checkpoint.nextExternalId() != null
                && items.stream().anyMatch(item -> checkpoint.nextExternalId().equals(item.externalId()))) {
            return true;
        }
        return checkpoint.lastCommittedExternalId() != null
                && items.stream().anyMatch(item -> checkpoint.lastCommittedExternalId().equals(item.externalId()));
    }

    private static String anchorOf(CrawlerCheckpoint checkpoint) {
        return checkpoint.nextExternalId() == null
                ? checkpoint.lastCommittedExternalId() : checkpoint.nextExternalId();
    }

    private record NearbyPage(int page, List<SourceListItem> items) {
    }

    private CrawlerTraversalMode traversalMode(CrawlerTaskLog job, CrawlerSchedule schedule,
                                               CrawlerCrawlMode crawlMode,
                                               CrawlerSourceSort sourceSort) {
        String configured = job.getTraversalMode() == null
                ? schedule.getTraversalMode() : job.getTraversalMode();
        if (configured != null && !configured.isBlank()) {
            try {
                return CrawlerTraversalMode.valueOf(configured.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                log.warn("忽略未知遍历模式，使用默认值: {}", configured);
            }
        }
        if (crawlMode == CrawlerCrawlMode.FULL) return CrawlerTraversalMode.MANUAL_FULL;
        return sourceSort == CrawlerSourceSort.TIME
                ? CrawlerTraversalMode.CONTINUOUS_SYNC
                : CrawlerTraversalMode.BACKFILL_CONTINUE;
    }

    private static CrawlerEndPolicy endPolicy(CrawlerSchedule schedule) {
        if (schedule.getEndPolicy() == null || schedule.getEndPolicy().isBlank()) {
            return CrawlerEndPolicy.HOLD_COMPLETED;
        }
        try {
            return CrawlerEndPolicy.valueOf(schedule.getEndPolicy().trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return CrawlerEndPolicy.HOLD_COMPLETED;
        }
    }

    private static CrawlerCheckpoint checkpointFromCursor(CrawlerScheduleCursor cursor) {
        return new CrawlerCheckpoint(CrawlerCheckpoint.CURRENT_VERSION,
                positiveOrDefault(cursor.getNextPage(), 1),
                Math.max(0, cursor.getNextItemIndex() == null ? 0 : cursor.getNextItemIndex()),
                cursor.getNextExternalId(), cursor.getLastCommittedExternalId());
    }

    private static CrawlerCursorState cursorState(String value) {
        if (value == null || value.isBlank()) return CrawlerCursorState.ACTIVE;
        try {
            return CrawlerCursorState.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return CrawlerCursorState.RECOVERY_REQUIRED;
        }
    }

    private static int positiveOrDefault(Integer value, int fallback) {
        return value == null ? Math.max(1, fallback) : Math.max(1, value);
    }

    private static Map<String, String> sourceFilters(CrawlerSchedule schedule) {
        if (schedule.getSourceFilters() == null || schedule.getSourceFilters().isEmpty()) {
            return Map.of();
        }
        return schedule.getSourceFilters().entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, Map.Entry::getValue));
    }

    private static boolean canContinue(MutableStats stats, CrawlerCrawlMode crawlMode,
                                       CrawlerTraversalMode traversalMode, int legacyMaxItems,
                                       int newItemLimit, int backfillItemLimit, int manualRunLimit,
                                       boolean cursorDriven) {
        if (!cursorDriven) {
            return stats.discovered < legacyMaxItems;
        }
        if (traversalMode == CrawlerTraversalMode.MANUAL_FULL || crawlMode == CrawlerCrawlMode.FULL) {
            return stats.discovered < manualRunLimit;
        }
        if (traversalMode == CrawlerTraversalMode.BACKFILL_CONTINUE) {
            return stats.backfillItems < backfillItemLimit;
        }
        if (legacyMaxItems != Integer.MAX_VALUE && stats.discovered < legacyMaxItems) {
            return true;
        }
        return stats.newItems < newItemLimit || stats.backfillItems < backfillItemLimit;
    }

    private static boolean canProcessNextItem(MutableStats stats, CrawlerCrawlMode crawlMode,
                                              CrawlerTraversalMode traversalMode, int legacyMaxItems,
                                              int newItemLimit, int backfillItemLimit, int manualRunLimit,
                                              boolean cursorDriven) {
        return canContinue(stats, crawlMode, traversalMode, legacyMaxItems,
                newItemLimit, backfillItemLimit, manualRunLimit, cursorDriven);
    }

    private static boolean isBackfillItem(CrawlerCrawlMode crawlMode,
                                          CrawlerTraversalMode traversalMode, int page,
                                          int latestRecentPages, boolean oldItem) {
        return traversalMode == CrawlerTraversalMode.BACKFILL_CONTINUE
                || (traversalMode == CrawlerTraversalMode.CONTINUOUS_SYNC
                && (page > latestRecentPages || oldItem))
                || (crawlMode == CrawlerCrawlMode.LATEST && page > latestRecentPages);
    }

    private static boolean shouldHoldAtEnd(CrawlerSourceSort sourceSort, CrawlerEndPolicy endPolicy) {
        return sourceSort != CrawlerSourceSort.TIME
                && endPolicy == CrawlerEndPolicy.HOLD_COMPLETED;
    }

    private void markCursorUnavailable(CrawlerScheduleCursor cursor, FetchResult fetch) {
        if (cursor == null || fetch == null) return;
        if (fetch.category() == FetchCategory.CHALLENGE_PAGE
                || fetch.category() == FetchCategory.FORBIDDEN) {
            cursorService.mark(cursor, CrawlerCursorState.SOURCE_UNAVAILABLE,
                    "来源不可用：" + fetch.category());
        }
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
                recordItemSuccessExisting(adapter, contentType, item,
                        observation.internalContentId());
                return new ItemProcessingResult(ItemOutcome.UNCHANGED, "list-unchanged", true);
            }
        }

        stats.detailAttempted++;
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
            recordItemSuccessExisting(adapter, contentType, item,
                    observation.internalContentId());
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
                recordItemSuccess(adapter, contentType, item, parsed, resolvedGenres,
                        internalContentId, persisted.added() ? "ADDED"
                                : persisted.updated() ? "UPDATED" : "UNCHANGED");
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

    private void recordItemSuccess(CrawlerSourceAdapter adapter, ContentType contentType,
                                   SourceListItem item, ParsedContent parsed,
                                   CrawlerGenreService.ResolvedGenres resolvedGenres,
                                   long internalContentId, String resultType) {
        Long jobId = executingJobId.get();
        if (jobId == null || itemSuccessService == null) return;
        try {
            itemSuccessService.record(jobId, adapter.sourceCode(), contentType, item, parsed,
                    resolvedGenres.names(), internalContentId, resultType);
        } catch (RuntimeException recordFailure) {
            log.warn("Failed to record crawler item success: jobId={}, source={}, externalId={}, error={}",
                    jobId, adapter.sourceCode(), item.externalId(),
                    recordFailure.getClass().getSimpleName());
        }
    }

    private void recordItemSuccessExisting(CrawlerSourceAdapter adapter,
                                            ContentType contentType, SourceListItem item,
                                            long internalContentId) {
        Long jobId = executingJobId.get();
        if (jobId == null || itemSuccessService == null) return;
        try {
            itemSuccessService.recordExisting(jobId, adapter.sourceCode(), contentType, item,
                    internalContentId);
        } catch (RuntimeException recordFailure) {
            log.warn("Failed to snapshot unchanged crawler item: jobId={}, source={}, externalId={}, error={}",
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

    private void recordProgress(CrawlerCheckpoint checkpoint, String currentItem, MutableStats stats,
                                CrawlerScheduleCursor cursor, boolean advanceCursor) {
        Long jobId = executingJobId.get();
        if (advanceCursor && cursor != null) {
            cursorService.advance(cursor, checkpoint.nextExternalId(),
                    checkpoint.lastCommittedExternalId(), checkpoint.nextPage(),
                    checkpoint.nextItemIndex(), currentItem,
                    CrawlerCursorState.ACTIVE.getCode(), null);
            stats.cursorAdvanced++;
        }
        if (jobId == null) return;
        try {
            String checkpointJson = objectMapper.writeValueAsString(checkpoint);
            taskLogMapper.updateProgress(jobId, checkpoint.nextPage(), currentItem,
                    stats.discovered, stats.fetchSucceeded, stats.parseSucceeded,
                    stats.added, stats.updated, stats.unchanged, stats.filtered, stats.failed,
                    checkpointJson, stats.pagesScanned, stats.listItemsScanned,
                    stats.detailAttempted, stats.cursorAdvanced, stats.newItems,
                    stats.backfillItems, CrawlerTime.nowUtc());
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
        private int pagesScanned;
        private int listItemsScanned;
        private int detailAttempted;
        private int cursorAdvanced;
        private int newItems;
        private int backfillItems;

        private CrawlExecutionSummary toSummary() {
            return new CrawlExecutionSummary(discovered, fetchSucceeded, parseSucceeded,
                    added, updated, unchanged, filtered, failed, pagesScanned,
                    listItemsScanned, detailAttempted, cursorAdvanced, newItems, backfillItems);
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
