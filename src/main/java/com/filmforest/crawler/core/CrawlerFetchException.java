package com.filmforest.crawler.core;

import com.filmforest.crawler.http.FetchResult;

public class CrawlerFetchException extends RuntimeException {

    private final transient FetchResult fetchResult;

    public CrawlerFetchException(String message, FetchResult fetchResult) {
        super(message + ": " + fetchResult.category());
        this.fetchResult = fetchResult;
    }

    public FetchResult getFetchResult() {
        return fetchResult;
    }
}
