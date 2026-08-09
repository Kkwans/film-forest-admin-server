package com.filmforest.crawler.model;

public record ParsedResource(
        Kind kind,
        String title,
        String url,
        String diskType,
        String password,
        String resolution,
        boolean hasSubtitle,
        boolean specialSubtitle,
        Integer season,
        Integer episodeNumber,
        String episodeTitle,
        int sourceOrder,
        String rawText,
        String sourcePageUrl,
        String playbackType
) {
    public enum Kind {
        MAGNET,
        CLOUD,
        ONLINE
    }
}
