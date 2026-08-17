package com.filmforest.crawler.dto;

import com.filmforest.crawler.model.CrawlerSourceCapabilities;

import java.util.List;
import java.util.Map;

public record CrawlerSourceDescriptor(
        Long id,
        String code,
        String name,
        String url,
        List<CrawlerAdapterDescriptor> adapters,
        Map<String, CrawlerSourceCapabilities> capabilities
) {
}
