package com.filmforest.crawler.dto;

import java.util.List;

public record CrawlerSourceDescriptor(
        Long id,
        String code,
        String name,
        String url,
        List<CrawlerAdapterDescriptor> adapters
) {
}
