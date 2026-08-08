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
        Map<String, String> importantHeaders
) {
    public boolean successful() {
        return category == FetchCategory.SUCCESS;
    }
}
