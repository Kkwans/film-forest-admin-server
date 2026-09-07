package com.filmforest.crawler.http;

import com.filmforest.crawler.config.CrawlerHttpProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class JavaHttpFetcherTest {

    @Test
    void classifiesHttpAndContentFailuresWithoutRetryingPermanentErrors() {
        assertThat(JavaHttpFetcher.classify(404, "text/html", "missing"))
                .isEqualTo(FetchCategory.NOT_FOUND);
        assertThat(JavaHttpFetcher.classify(403, "text/html", "forbidden"))
                .isEqualTo(FetchCategory.FORBIDDEN);
        assertThat(JavaHttpFetcher.classify(403, "text/html", "<div class=cf-turnstile></div>"))
                .isEqualTo(FetchCategory.CHALLENGE_PAGE);
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
    void challengePageIsRetriedAfterWaitUsingTheSameHttpSession() throws IOException {
        CrawlerHttpProperties properties = new CrawlerHttpProperties();
        properties.setMaxAttempts(2);
        properties.setChallengeRetryDelay(Duration.ZERO);
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setRequestTimeout(Duration.ofSeconds(1));

        AtomicInteger requests = new AtomicInteger();
        AtomicBoolean challengeCookieWasReused = new AtomicBoolean();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/challenge", exchange -> {
            int requestNumber = requests.incrementAndGet();
            if (requestNumber == 1) {
                exchange.getResponseHeaders().add("Set-Cookie", "challenge=passed; Path=/");
            } else if (exchange.getRequestHeaders().getFirst("Cookie") != null
                    && exchange.getRequestHeaders().getFirst("Cookie").contains("challenge=passed")) {
                challengeCookieWasReused.set(true);
            }
            String body = requestNumber == 1
                    ? "<div class=cf-turnstile></div>"
                    : "<html><a href='/mv/1.html'>ok</a></html>";
            byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            try {
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            JavaHttpFetcher fetcher = new JavaHttpFetcher(properties);
            FetchResult result = fetcher.fetch(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/challenge"),
                    Map.of(), 0, new AtomicBoolean(false));

            assertThat(result.category()).isEqualTo(FetchCategory.SUCCESS);
            assertThat(result.attemptCount()).isEqualTo(2);
            assertThat(requests).hasValue(2);
            assertThat(challengeCookieWasReused).isTrue();
        } finally {
            server.stop(0);
        }
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
    void htmlContentNegotiationDoesNotAdvertiseJson() {
        assertThat(JavaHttpFetcher.HTML_ACCEPT)
                .isEqualTo("text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
                .doesNotContain("application/json");
    }

    @Test
    void sensitiveQueryValuesNeverAppearInFetchResultUris() {
        URI uri = URI.create("https://api.example.test/search?query=title&api_key=secret-value&page=1");

        URI redacted = JavaHttpFetcher.redactUri(uri, Set.of("api_key"));

        assertThat(redacted.toString()).contains("query=title", "api_key=REDACTED", "page=1");
        assertThat(redacted.toString()).doesNotContain("secret-value");
    }

    @Test
    void fetchProgressCarriesAttemptAndElapsedWaitForVisibleRequestState() {
        HttpFetcher.FetchProgress progress = new HttpFetcher.FetchProgress(
                2, 3, 7_500L, HttpFetcher.FetchProgressPhase.WAITING,
                "来源请求仍在等待响应");

        assertThat(progress.attempt()).isEqualTo(2);
        assertThat(progress.maxAttempts()).isEqualTo(3);
        assertThat(progress.elapsedMs()).isEqualTo(7_500L);
        assertThat(progress.phase()).isEqualTo(HttpFetcher.FetchProgressPhase.WAITING);
        assertThat(progress.message()).contains("等待");
    }
}
