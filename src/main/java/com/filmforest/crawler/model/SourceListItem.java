package com.filmforest.crawler.model;

public record SourceListItem(
        String externalId,
        String sourceUrl,
        String title,
        String posterUrl,
        int sourceOrder
) {
}
