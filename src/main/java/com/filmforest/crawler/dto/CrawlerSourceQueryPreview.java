package com.filmforest.crawler.dto;

import java.util.List;

public record CrawlerSourceQueryPreview(
        String status,
        String sourceCode,
        String contentType,
        String sort,
        String normalizedUri,
        String message,
        List<String> sampleExternalIds,
        int sampleCount
) {
    public CrawlerSourceQueryPreview {
        sampleExternalIds = sampleExternalIds == null ? List.of() : List.copyOf(sampleExternalIds);
    }
}
