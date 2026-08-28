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
        String playbackType,
        String providerName,
        Long sizeBytes
) {
    /**
     * 保持已有解析器和测试的构造契约；来源展示名称在旧数据中不存在时保持为空。
     */
    public ParsedResource(Kind kind, String title, String url, String diskType, String password,
                          String resolution, boolean hasSubtitle, boolean specialSubtitle,
                          Integer season, Integer episodeNumber, String episodeTitle,
                          int sourceOrder, String rawText, String sourcePageUrl,
                          String playbackType) {
        this(kind, title, url, diskType, password, resolution, hasSubtitle, specialSubtitle,
                season, episodeNumber, episodeTitle, sourceOrder, rawText, sourcePageUrl,
                playbackType, null, null);
    }

    public ParsedResource(Kind kind, String title, String url, String diskType, String password,
                          String resolution, boolean hasSubtitle, boolean specialSubtitle,
                          Integer season, Integer episodeNumber, String episodeTitle,
                          int sourceOrder, String rawText, String sourcePageUrl,
                          String playbackType, String providerName) {
        this(kind, title, url, diskType, password, resolution, hasSubtitle, specialSubtitle,
                season, episodeNumber, episodeTitle, sourceOrder, rawText, sourcePageUrl,
                playbackType, providerName, null);
    }

    public enum Kind {
        MAGNET,
        CLOUD,
        ONLINE
    }
}
