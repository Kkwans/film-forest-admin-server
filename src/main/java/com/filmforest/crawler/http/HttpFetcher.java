package com.filmforest.crawler.http;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public interface HttpFetcher {

    default FetchResult fetch(URI uri, Map<String, String> headers, int rateLimitMs,
                              AtomicBoolean cancellation) {
        return fetch(uri, headers, rateLimitMs, cancellation, Set.of());
    }

    FetchResult fetch(URI uri, Map<String, String> headers, int rateLimitMs,
                      AtomicBoolean cancellation, Set<String> sensitiveQueryParameters);
}
