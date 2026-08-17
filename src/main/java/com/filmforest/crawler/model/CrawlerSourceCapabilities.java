package com.filmforest.crawler.model;

import java.util.Set;

/** 来源适配器真实支持的查询能力，不代表绕过 challenge 后的可用性。 */
public record CrawlerSourceCapabilities(
        String sourceCode,
        String contentType,
        Set<String> supportedSorts,
        Set<String> supportedFilters,
        boolean verified,
        String availability,
        String message
) {
    public CrawlerSourceCapabilities {
        supportedSorts = supportedSorts == null ? Set.of() : Set.copyOf(supportedSorts);
        supportedFilters = supportedFilters == null ? Set.of() : Set.copyOf(supportedFilters);
    }

    public boolean supportsSort(String sort) {
        return sort != null && supportedSorts.contains(sort);
    }

    public boolean supportsFilter(String filter) {
        return filter != null && supportedFilters.contains(filter);
    }
}
