package com.filmforest.crawler.http;

import java.net.URI;
import java.util.Map;

public record FetchResult(
        URI requestUrl,
        URI finalUrl,
        int statusCode,
        String contentType,
        String body,
        long elapsedMs,
        FetchCategory category,
        boolean retryable,
        Map<String, String> importantHeaders,
        int attemptCount
) {
    public FetchResult(URI requestUrl, URI finalUrl, int statusCode, String contentType,
                       String body, long elapsedMs, FetchCategory category, boolean retryable,
                       Map<String, String> importantHeaders) {
        this(requestUrl, finalUrl, statusCode, contentType, body, elapsedMs,
                category, retryable, importantHeaders, 1);
    }

    public FetchResult {
        attemptCount = Math.max(1, attemptCount);
    }

    public boolean successful() {
        return category == FetchCategory.SUCCESS;
    }

    public FetchResult withAttemptCount(int attempts) {
        return new FetchResult(requestUrl, finalUrl, statusCode, contentType, body, elapsedMs,
                category, retryable, importantHeaders, attempts);
    }
}
