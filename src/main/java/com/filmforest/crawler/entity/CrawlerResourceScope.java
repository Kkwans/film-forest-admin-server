package com.filmforest.crawler.entity;

import com.filmforest.crawler.model.ParsedResource;

import java.util.Locale;

/** Defines which resource kinds a crawler job is allowed to parse and persist. */
public enum CrawlerResourceScope {
    DOWNLOADS,
    ONLINE,
    ALL;

    public static CrawlerResourceScope fromCode(String value) {
        // Null is the compatibility value for pre-V29 schedules and unit callers;
        // persisted V29 rows are explicitly migrated to DOWNLOADS.
        if (value == null || value.isBlank()) return ALL;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Unsupported crawler resourceScope: " + value, invalid);
        }
    }

    public boolean includes(ParsedResource.Kind kind) {
        return this == ALL || (this == DOWNLOADS && kind != ParsedResource.Kind.ONLINE)
                || (this == ONLINE && kind == ParsedResource.Kind.ONLINE);
    }
}
