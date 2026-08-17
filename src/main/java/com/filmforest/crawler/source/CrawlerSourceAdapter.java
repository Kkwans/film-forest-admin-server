package com.filmforest.crawler.source;

import com.filmforest.common.type.ContentType;
import com.filmforest.crawler.model.ParsedContent;
import com.filmforest.crawler.model.CrawlerSourceCapabilities;
import com.filmforest.crawler.model.CrawlerSourceQuery;
import com.filmforest.crawler.model.SourceListItem;

import java.net.URI;
import java.util.List;
import java.util.Set;

public interface CrawlerSourceAdapter {

    String sourceCode();

    default String displayName() {
        return sourceCode();
    }

    Set<String> aliases();

    URI listUri(ContentType contentType, int page);

    /**
     * 类型化来源查询入口。旧适配器默认只支持旧的分页 URL，不能静默接受新的排序/筛选。
     */
    default URI listUri(CrawlerSourceQuery query) {
        if (query.sort() != com.filmforest.crawler.entity.CrawlerSourceSort.TIME
                || !query.sourceFilters().isEmpty()) {
            throw new IllegalArgumentException("来源适配器未声明所请求的排序或筛选能力");
        }
        return listUri(query.contentType(), query.page());
    }

    default CrawlerSourceCapabilities capabilities(ContentType contentType) {
        return new CrawlerSourceCapabilities(
                sourceCode(), contentType.value(),
                Set.of("TIME"), Set.of(), false, "UNVERIFIED",
                "来源查询契约尚未通过真实页面或脱敏 fixture 验证");
    }

    List<SourceListItem> parseList(String html, URI finalUri);

    ParsedContent parseDetail(ContentType contentType, String html, URI finalUri);
}
