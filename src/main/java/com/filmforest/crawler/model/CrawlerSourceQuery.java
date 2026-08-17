package com.filmforest.crawler.model;

import com.filmforest.common.type.ContentType;
import com.filmforest.crawler.entity.CrawlerSourceSort;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 来源查询的内部类型化契约。sourceFilters 只允许来源能力声明过的键，
 * 标准题材后置过滤不进入此对象。
 */
public record CrawlerSourceQuery(
        ContentType contentType,
        CrawlerSourceSort sort,
        Map<String, String> sourceFilters,
        int page
) {

    public CrawlerSourceQuery {
        if (contentType == null) throw new IllegalArgumentException("contentType 不能为空");
        if (sort == null) sort = CrawlerSourceSort.TIME;
        if (page < 1) page = 1;
        sourceFilters = sourceFilters == null
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(sourceFilters));
    }

    public String filter(String key) {
        return sourceFilters.get(key);
    }

    public CrawlerSourceQuery withPage(int nextPage) {
        return new CrawlerSourceQuery(contentType, sort, sourceFilters, nextPage);
    }
}
