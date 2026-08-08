package com.filmforest.crawler.source;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Comparator;

@Component
public class SourceAdapterRegistry {

    private final Map<String, CrawlerSourceAdapter> adapters;
    private final List<CrawlerSourceAdapter> primaryAdapters;

    public SourceAdapterRegistry(List<CrawlerSourceAdapter> sourceAdapters) {
        Map<String, CrawlerSourceAdapter> registered = new HashMap<>();
        for (CrawlerSourceAdapter adapter : sourceAdapters) {
            register(registered, adapter.sourceCode(), adapter);
            adapter.aliases().forEach(alias -> register(registered, alias, adapter));
        }
        this.adapters = Map.copyOf(registered);
        this.primaryAdapters = sourceAdapters.stream()
                .sorted(Comparator.comparing(CrawlerSourceAdapter::sourceCode))
                .toList();
    }

    public CrawlerSourceAdapter require(String sourceSite) {
        String key = normalize(sourceSite);
        CrawlerSourceAdapter adapter = adapters.get(key);
        if (adapter == null) {
            throw new IllegalArgumentException("Unsupported crawler source: " + sourceSite);
        }
        return adapter;
    }

    public List<CrawlerSourceAdapter> availableAdapters() {
        return primaryAdapters;
    }

    private static void register(Map<String, CrawlerSourceAdapter> target, String key,
                                 CrawlerSourceAdapter adapter) {
        CrawlerSourceAdapter existing = target.putIfAbsent(normalize(key), adapter);
        if (existing != null && existing != adapter) {
            throw new IllegalStateException("Duplicate crawler source alias: " + key);
        }
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Crawler source is required");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
