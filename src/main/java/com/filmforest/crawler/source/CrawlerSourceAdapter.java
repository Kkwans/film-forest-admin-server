package com.filmforest.crawler.source;

import com.filmforest.common.type.ContentType;
import com.filmforest.crawler.model.ParsedContent;
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

    List<SourceListItem> parseList(String html, URI finalUri);

    ParsedContent parseDetail(ContentType contentType, String html, URI finalUri);
}
