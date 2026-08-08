package com.filmforest.crawler.http;

import com.filmforest.crawler.config.CrawlerHttpProperties;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class JavaHttpFetcherTest {

    @Test
    void classifiesHttpAndContentFailuresWithoutRetryingPermanentErrors() {
        assertThat(JavaHttpFetcher.classify(404, "text/html", "missing"))
                .isEqualTo(FetchCategory.NOT_FOUND);
        assertThat(JavaHttpFetcher.classify(403, "text/html", "forbidden"))
                .isEqualTo(FetchCategory.FORBIDDEN);
        assertThat(JavaHttpFetcher.classify(429, "application/json", "{}"))
                .isEqualTo(FetchCategory.RATE_LIMITED);
        assertThat(JavaHttpFetcher.classify(503, "text/html", "later"))
                .isEqualTo(FetchCategory.SERVER_ERROR);
        assertThat(JavaHttpFetcher.classify(200, "image/jpeg", "binary"))
                .isEqualTo(FetchCategory.INVALID_CONTENT_TYPE);
        assertThat(JavaHttpFetcher.classify(200, "text/html", "  "))
                .isEqualTo(FetchCategory.EMPTY_BODY);
        assertThat(JavaHttpFetcher.classify(200, "text/html", "<div class=cf-turnstile></div>"))
                .isEqualTo(FetchCategory.CHALLENGE_PAGE);
    }

    @Test
    void parsesRetryAfterSecondsAndRejectsInvalidValues() {
        assertThat(JavaHttpFetcher.parseRetryAfter("3")).isEqualTo(3_000L);
        assertThat(JavaHttpFetcher.parseRetryAfter("not-a-date")).isNull();
    }

    @Test
    void cancelledBeforeRateLimitWaitDoesNotSendRequest() {
        CrawlerHttpProperties properties = new CrawlerHttpProperties();
        properties.setConnectTimeout(Duration.ofMillis(50));
        properties.setRequestTimeout(Duration.ofMillis(50));
        JavaHttpFetcher fetcher = new JavaHttpFetcher(properties);

        FetchResult result = fetcher.fetch(URI.create("http://127.0.0.1:1/never"), Map.of(),
                1_000, new AtomicBoolean(true));

        assertThat(result.category()).isEqualTo(FetchCategory.CANCELLED);
        assertThat(result.retryable()).isFalse();
    }

    @Test
    void sensitiveQueryValuesNeverAppearInFetchResultUris() {
        URI uri = URI.create("https://api.example.test/search?query=title&api_key=secret-value&page=1");

        URI redacted = JavaHttpFetcher.redactUri(uri, Set.of("api_key"));

        assertThat(redacted.toString()).contains("query=title", "api_key=REDACTED", "page=1");
        assertThat(redacted.toString()).doesNotContain("secret-value");
    }
}
