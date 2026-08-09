package com.filmforest.crawler.source;

import com.filmforest.crawler.http.HttpFetcher;
import com.filmforest.crawler.model.ParsedContent;

import java.util.concurrent.atomic.AtomicBoolean;

/** 仅供需要额外公开页面解析的来源适配器实现。 */
public interface CrawlerResourceEnricher {

    ParsedContent enrichResources(ParsedContent parsed, HttpFetcher httpFetcher,
                                  int rateLimitMs, AtomicBoolean cancellation);
}
