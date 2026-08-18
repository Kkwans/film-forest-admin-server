package com.filmforest.crawler.service;

import com.filmforest.common.type.ContentType;
import com.filmforest.crawler.dto.CrawlerSourceQueryPreview;
import com.filmforest.crawler.dto.CrawlerSourceQueryPreviewRequest;
import com.filmforest.crawler.http.FetchCategory;
import com.filmforest.crawler.http.FetchResult;
import com.filmforest.crawler.http.HttpFetcher;
import com.filmforest.crawler.model.CrawlerSourceCapabilities;
import com.filmforest.crawler.model.CrawlerSourceQuery;
import com.filmforest.crawler.model.SourceListItem;
import com.filmforest.crawler.entity.CrawlerSourceSort;
import com.filmforest.crawler.source.CrawlerSourceAdapter;
import com.filmforest.crawler.source.SourceAdapterRegistry;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class CrawlerSourceQueryPreviewService {

    private static final int PREVIEW_RATE_LIMIT_MS = 2000;

    private final SourceAdapterRegistry adapterRegistry;
    private final HttpFetcher httpFetcher;

    public CrawlerSourceQueryPreviewService(SourceAdapterRegistry adapterRegistry,
                                            HttpFetcher httpFetcher) {
        this.adapterRegistry = adapterRegistry;
        this.httpFetcher = httpFetcher;
    }

    public CrawlerSourceQueryPreview preview(CrawlerSourceQueryPreviewRequest request) {
        ContentType contentType = parseContentType(request.contentType());
        CrawlerSourceAdapter adapter = adapterRegistry.require(request.sourceCode());
        CrawlerSourceSort sort = CrawlerSourceSort.fromCode(request.sort());
        CrawlerSourceCapabilities capabilities = adapter.capabilities(contentType);
        // 能力尚未通过真实来源页面验证时，先返回来源不可用，而不是把用户选择的
        // 评分/热度误判成“明确不支持”。配置仍可保存为 NEEDS_REVIEW，待来源恢复后
        // 再执行真实的排序和筛选能力校验。
        if (!capabilities.verified()
                || "CHALLENGE".equalsIgnoreCase(capabilities.availability())
                || "UNAVAILABLE".equalsIgnoreCase(capabilities.availability())) {
            return result("SOURCE_UNAVAILABLE", adapter, contentType, sort,
                    null, capabilities.message() == null || capabilities.message().isBlank()
                            ? "来源当前不可用，未写入数据或推进游标" : capabilities.message(), List.of());
        }
        if (!capabilities.supportsSort(sort.getCode())) {
            return result("UNSUPPORTED", adapter, contentType, sort,
                    null, "来源未声明支持“" + sort.getCode() + "”排序", List.of());
        }

        Map<String, String> filters = normalizeFilters(request.sourceFilters());
        String unsupported = filters.keySet().stream()
                .filter(key -> !capabilities.supportsFilter(key))
                .findFirst().orElse(null);
        if (unsupported != null) {
            return result("UNSUPPORTED", adapter, contentType, sort,
                    null, "来源未声明支持筛选字段：" + unsupported, List.of());
        }

        CrawlerSourceQuery query = new CrawlerSourceQuery(contentType, sort, filters,
                request.page() == null ? 1 : request.page());
        URI uri;
        try {
            uri = adapter.listUri(query);
        } catch (IllegalArgumentException invalid) {
            return result("UNSUPPORTED", adapter, contentType, sort,
                    null, invalid.getMessage(), List.of());
        }

        FetchResult fetched = httpFetcher.fetch(uri, Map.of(), PREVIEW_RATE_LIMIT_MS,
                new AtomicBoolean(false));
        if (!fetched.successful()) {
            String status = fetched.category() == FetchCategory.CHALLENGE_PAGE
                    || fetched.category() == FetchCategory.FORBIDDEN
                    ? "SOURCE_UNAVAILABLE" : "NEEDS_REVIEW";
            return result(status, adapter, contentType, sort, uri,
                    "来源返回 " + fetched.category().name() + "，未写入数据或推进游标", List.of());
        }
        try {
            List<String> externalIds = adapter.parseList(fetched.body(), fetched.finalUrl()).stream()
                    .map(SourceListItem::externalId)
                    .filter(id -> id != null && !id.isBlank())
                    .limit(10)
                    .toList();
            return result("VALIDATED", adapter, contentType, sort, uri,
                    "来源查询和列表结构验证通过", externalIds);
        } catch (RuntimeException parseFailure) {
            return result("NEEDS_REVIEW", adapter, contentType, sort, uri,
                    "列表结构无法解析：" + parseFailure.getClass().getSimpleName(), List.of());
        }
    }

    private CrawlerSourceQueryPreview result(String status, CrawlerSourceAdapter adapter,
                                              ContentType contentType, CrawlerSourceSort sort,
                                              URI uri, String message, List<String> ids) {
        return new CrawlerSourceQueryPreview(status, adapter.sourceCode(), contentType.value(),
                sort.getCode(), uri == null ? null : uri.toString(), message, ids, ids.size());
    }

    private static Map<String, String> normalizeFilters(Map<String, String> input) {
        if (input == null || input.isEmpty()) return Map.of();
        Map<String, String> normalized = new LinkedHashMap<>();
        input.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
                normalized.put(key.trim(), value.trim());
            }
        });
        return Map.copyOf(normalized);
    }

    private static ContentType parseContentType(String value) {
        if ("short".equals(value)) return ContentType.SHORT_DRAMA;
        return ContentType.fromValue(value)
                .orElseThrow(() -> new IllegalArgumentException("不支持的内容类型: " + value));
    }
}
