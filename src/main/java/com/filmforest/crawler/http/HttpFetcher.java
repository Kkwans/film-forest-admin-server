package com.filmforest.crawler.http;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public interface HttpFetcher {

    FetchResult fetch(URI uri, Map<String, String> headers, int rateLimitMs, AtomicBoolean cancellation);
}
