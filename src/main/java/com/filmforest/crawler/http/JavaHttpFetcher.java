package com.filmforest.crawler.http;

import com.filmforest.crawler.config.CrawlerHttpProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class JavaHttpFetcher implements HttpFetcher {

    private static final Set<String> IMPORTANT_HEADERS = Set.of(
            "content-type", "content-length", "retry-after", "location", "etag", "last-modified");
    private static final Set<String> CHALLENGE_MARKERS = Set.of(
            "cf-chl-", "cf-turnstile", "captcha", "challenge-platform", "verify you are human");

    private final CrawlerHttpProperties properties;
    private final HttpClient httpClient;

    public JavaHttpFetcher(CrawlerHttpProperties properties) {
        this(properties, createClient(properties));
    }

    JavaHttpFetcher(CrawlerHttpProperties properties, HttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    @Override
    public FetchResult fetch(URI uri, Map<String, String> headers, int rateLimitMs,
                             AtomicBoolean cancellation) {
        if (!sleepCancellable(Math.max(0, rateLimitMs), cancellation)) {
            return cancelled(uri, 0L);
        }

        int attempts = Math.max(1, properties.getMaxAttempts());
        FetchResult last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            if (isCancelled(cancellation)) {
                return cancelled(uri, last == null ? 0L : last.elapsedMs());
            }
            last = fetchOnce(uri, headers, cancellation);
            if (!last.retryable() || attempt == attempts) {
                return last;
            }
            long delayMs = retryDelayMs(last, attempt);
            if (!sleepCancellable(delayMs, cancellation)) {
                return cancelled(uri, last.elapsedMs());
            }
        }
        return last;
    }

    private FetchResult fetchOnce(URI uri, Map<String, String> headers, AtomicBoolean cancellation) {
        long started = System.nanoTime();
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(properties.getRequestTimeout())
                .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
                .header("User-Agent", properties.getUserAgent());
        headers.forEach(builder::header);

        CompletableFuture<HttpResponse<byte[]>> future = httpClient.sendAsync(
                builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        try {
            HttpResponse<byte[]> response = await(future, cancellation);
            long elapsedMs = elapsedMs(started);
            if (response == null) {
                return cancelled(uri, elapsedMs);
            }
            byte[] bytes = response.body() == null ? new byte[0] : response.body();
            String contentType = response.headers().firstValue("content-type").orElse("");
            Map<String, String> importantHeaders = importantHeaders(response);
            if (bytes.length > properties.getMaxBodyBytes()) {
                return new FetchResult(uri, response.uri(), response.statusCode(), contentType, "",
                        elapsedMs, FetchCategory.INVALID_CONTENT_TYPE, false, importantHeaders);
            }
            String body = new String(bytes, StandardCharsets.UTF_8);
            FetchCategory category = classify(response.statusCode(), contentType, body);
            boolean retryable = category == FetchCategory.RATE_LIMITED
                    || category == FetchCategory.SERVER_ERROR
                    || category == FetchCategory.NETWORK_ERROR;
            log.atDebug().log("HTTP fetch {} {} -> {} in {} ms", response.statusCode(), safeUri(uri),
                    category, elapsedMs);
            return new FetchResult(uri, response.uri(), response.statusCode(), contentType, body,
                    elapsedMs, category, retryable, importantHeaders);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            if (cancellation != null) {
                cancellation.set(true);
            }
            return cancelled(uri, elapsedMs(started));
        } catch (ExecutionException | TimeoutException error) {
            future.cancel(true);
            long elapsedMs = elapsedMs(started);
            log.atWarn().log("HTTP fetch failed for {}: {}", safeUri(uri), error.getClass().getSimpleName());
            return new FetchResult(uri, uri, 0, "", "", elapsedMs,
                    FetchCategory.NETWORK_ERROR, true, Map.of());
        }
    }

    private HttpResponse<byte[]> await(CompletableFuture<HttpResponse<byte[]>> future,
                                       AtomicBoolean cancellation)
            throws InterruptedException, ExecutionException, TimeoutException {
        while (!future.isDone()) {
            if (isCancelled(cancellation)) {
                future.cancel(true);
                return null;
            }
            try {
                return future.get(100, TimeUnit.MILLISECONDS);
            } catch (TimeoutException ignored) {
                // Re-check cancellation without hiding the request-level timeout.
            }
        }
        return future.get(100, TimeUnit.MILLISECONDS);
    }

    static FetchCategory classify(int statusCode, String contentType, String body) {
        if (statusCode == 404) {
            return FetchCategory.NOT_FOUND;
        }
        if (statusCode == 401 || statusCode == 403) {
            return FetchCategory.FORBIDDEN;
        }
        if (statusCode == 429) {
            return FetchCategory.RATE_LIMITED;
        }
        if (statusCode >= 500) {
            return FetchCategory.SERVER_ERROR;
        }
        if (statusCode < 200 || statusCode >= 300) {
            return FetchCategory.NETWORK_ERROR;
        }
        String normalizedType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (!normalizedType.contains("text/html")
                && !normalizedType.contains("application/xhtml+xml")
                && !normalizedType.contains("application/json")) {
            return FetchCategory.INVALID_CONTENT_TYPE;
        }
        if (body == null || body.isBlank()) {
            return FetchCategory.EMPTY_BODY;
        }
        String normalizedBody = body.toLowerCase(Locale.ROOT);
        if (CHALLENGE_MARKERS.stream().anyMatch(normalizedBody::contains)) {
            return FetchCategory.CHALLENGE_PAGE;
        }
        return FetchCategory.SUCCESS;
    }

    private long retryDelayMs(FetchResult result, int attempt) {
        String retryAfter = result.importantHeaders().get("retry-after");
        Long headerDelay = parseRetryAfter(retryAfter);
        if (headerDelay != null) {
            return Math.min(headerDelay, Duration.ofMinutes(2).toMillis());
        }
        long base = Math.max(0L, properties.getRetryBaseDelay().toMillis());
        return Math.min(base * (1L << Math.min(attempt - 1, 6)), Duration.ofSeconds(30).toMillis());
    }

    static Long parseRetryAfter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Math.max(0L, Long.parseLong(value.trim()) * 1000L);
        } catch (NumberFormatException ignored) {
            try {
                Instant retryAt = ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME)
                        .toInstant();
                return Math.max(0L, Duration.between(Instant.now(), retryAt).toMillis());
            } catch (RuntimeException invalidDate) {
                return null;
            }
        }
    }

    private static Map<String, String> importantHeaders(HttpResponse<?> response) {
        Map<String, String> headers = new LinkedHashMap<>();
        IMPORTANT_HEADERS.forEach(name -> response.headers().firstValue(name)
                .ifPresent(value -> headers.put(name, value)));
        return Map.copyOf(headers);
    }

    private static HttpClient createClient(CrawlerHttpProperties properties) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .version(HttpClient.Version.HTTP_2);
        if (properties.isProxyEnabled()) {
            builder.proxy(ProxySelector.of(new InetSocketAddress(
                    properties.getProxyHost(), properties.getProxyPort())));
        }
        return builder.build();
    }

    private static boolean sleepCancellable(long delayMs, AtomicBoolean cancellation) {
        long remaining = delayMs;
        while (remaining > 0) {
            if (isCancelled(cancellation)) {
                return false;
            }
            long slice = Math.min(remaining, 100L);
            try {
                Thread.sleep(slice);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                if (cancellation != null) {
                    cancellation.set(true);
                }
                return false;
            }
            remaining -= slice;
        }
        return !isCancelled(cancellation);
    }

    private static boolean isCancelled(AtomicBoolean cancellation) {
        return cancellation != null && cancellation.get();
    }

    private static FetchResult cancelled(URI uri, long elapsedMs) {
        return new FetchResult(uri, uri, 0, "", "", elapsedMs,
                FetchCategory.CANCELLED, false, Map.of());
    }

    private static String safeUri(URI uri) {
        return uri.getScheme() + "://" + uri.getAuthority() + uri.getPath();
    }

    private static long elapsedMs(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }
}
