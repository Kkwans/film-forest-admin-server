package com.filmforest.crawler.entity;

import java.util.Locale;

/** 来源列表的排序语义；不再使用历史 priority 文案。 */
public enum CrawlerSourceSort {
    TIME("TIME"),
    POPULARITY("POPULARITY"),
    RATING("RATING");

    private final String code;

    CrawlerSourceSort(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static CrawlerSourceSort fromCode(String value) {
        if (value == null || value.isBlank()) return TIME;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (CrawlerSourceSort sort : values()) {
            if (sort.code.equals(normalized)
                    || (sort == RATING && "BY_SCORE".equals(normalized))
                    || (sort == POPULARITY && "BY_HOT".equals(normalized))) {
                return sort;
            }
        }
        throw new IllegalArgumentException("不支持的来源排序: " + value);
    }
}
