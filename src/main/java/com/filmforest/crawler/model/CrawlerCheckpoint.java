package com.filmforest.crawler.model;

import java.util.List;

public record CrawlerCheckpoint(
        int version,
        int nextPage,
        int nextItemIndex,
        String nextExternalId,
        String lastCommittedExternalId
) {
    public static final int CURRENT_VERSION = 1;

    public static CrawlerCheckpoint atPage(int page) {
        return new CrawlerCheckpoint(CURRENT_VERSION, Math.max(1, page), 0, null, null);
    }

    public static CrawlerCheckpoint beforeItem(int page, int itemIndex, String externalId,
                                               String lastCommittedExternalId) {
        return new CrawlerCheckpoint(CURRENT_VERSION, Math.max(1, page),
                Math.max(0, itemIndex), externalId, lastCommittedExternalId);
    }

    public CrawlerCheckpoint normalized(int fallbackPage) {
        if (version < 0 || version > CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported crawler checkpoint version: " + version);
        }
        return new CrawlerCheckpoint(CURRENT_VERSION,
                nextPage > 0 ? nextPage : Math.max(1, fallbackPage),
                Math.max(0, nextItemIndex), blankToNull(nextExternalId),
                blankToNull(lastCommittedExternalId));
    }

    public int resumeItemIndex(List<SourceListItem> items) {
        if (nextExternalId != null) {
            for (int index = 0; index < items.size(); index++) {
                if (nextExternalId.equals(items.get(index).externalId())) {
                    return index;
                }
            }
        }
        if (lastCommittedExternalId != null) {
            for (int index = 0; index < items.size(); index++) {
                if (lastCommittedExternalId.equals(items.get(index).externalId())) {
                    return Math.min(index + 1, items.size());
                }
            }
        }
        return Math.min(Math.max(0, nextItemIndex), items.size());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
