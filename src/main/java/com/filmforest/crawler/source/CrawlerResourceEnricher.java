package com.filmforest.crawler.source;

import com.filmforest.crawler.http.HttpFetcher;
import com.filmforest.crawler.model.ParsedContent;

import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicBoolean;

/** 仅供需要额外公开页面解析的来源适配器实现。 */
public interface CrawlerResourceEnricher {

    ParsedContent enrichResources(ParsedContent parsed, HttpFetcher httpFetcher,
                                  int rateLimitMs, AtomicBoolean cancellation);

    /**
     * 允许来源在解析长耗时资源页时报告可见进度；默认实现保持旧适配器兼容。
     */
    default ParsedContent enrichResources(ParsedContent parsed, HttpFetcher httpFetcher,
                                          int rateLimitMs, AtomicBoolean cancellation,
                                          Consumer<Progress> progress) {
        return enrichResources(parsed, httpFetcher, rateLimitMs, cancellation);
    }

    record Progress(String stage, int percent, String message) {
    }
}
