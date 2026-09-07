package com.filmforest.crawler.http;

import com.filmforest.crawler.config.CrawlerHttpProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.CookieManager;
import java.net.CookiePolicy;
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
import java.util.function.Consumer;

@Slf4j
@Component
public class JavaHttpFetcher implements HttpFetcher {

    static final String HTML_ACCEPT = "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8";
    private static final Set<String> IMPORTANT_HEADERS = Set.of(
            "content-type", "content-length", "retry-after", "location", "etag", "last-modified");
    private static final Set<String> CHALLENGE_MARKERS = Set.of(
            "cf-chl-", "cf-turnstile", "captcha", "challenge-platform", "verify you are human");

    private final CrawlerHttpProperties properties;
    private final HttpClient httpClient;

    @Autowired
    public JavaHttpFetcher(CrawlerHttpProperties properties) {
        this(properties, createClient(properties));
    }

    JavaHttpFetcher(CrawlerHttpProperties properties, HttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    @Override
    public boolean supportsProgressCallbacks() {
        return true;
    }

    @Override
    public FetchResult fetch(URI uri, Map<String, String> headers, int rateLimitMs,
                             AtomicBoolean cancellation, Set<String> sensitiveQueryParameters) {
        return fetch(uri, headers, rateLimitMs, cancellation, sensitiveQueryParameters, progress -> { });
    }

    @Override
    public FetchResult fetch(URI uri, Map<String, String> headers, int rateLimitMs,
                             AtomicBoolean cancellation, Set<String> sensitiveQueryParameters,
                             Consumer<FetchProgress> progress) {
        URI publicUri = redactUri(uri, sensitiveQueryParameters);
        Consumer<FetchProgress> observer = progress == null ? ignored -> { } : progress;
        int attempts = Math.max(1, properties.getMaxAttempts());
        long waitingStarted = System.nanoTime();
        emit(observer, 0, attempts, 0L, FetchProgressPhase.WAITING,
                "等待来源请求间隔");
        if (!sleepCancellable(Math.max(0, rateLimitMs), cancellation, observer,
                0, attempts, waitingStarted, FetchProgressPhase.WAITING, "等待来源请求间隔")) {
            return cancelled(publicUri, 0L);
        }

        FetchResult last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            if (isCancelled(cancellation)) {
                return cancelled(publicUri, last == null ? 0L : last.elapsedMs());
            }
            emit(observer, attempt, attempts, 0L, FetchProgressPhase.REQUESTING,
                    "正在请求来源详情（第 " + attempt + "/" + attempts + " 次）");
            last = fetchOnce(uri, publicUri, headers, cancellation, sensitiveQueryParameters,
                    observer, attempt, attempts)
                    .withAttemptCount(attempt);
            if (!last.retryable() || attempt == attempts) {
                return last;
            }
            long delayMs = retryDelayMs(last, attempt);
            long retryStarted = System.nanoTime();
            if (!sleepCancellable(delayMs, cancellation, observer, attempt, attempts,
                    retryStarted, FetchProgressPhase.RETRYING,
                    retryMessage(last, delayMs))) {
                return cancelled(publicUri, last.elapsedMs());
            }
        }
        return last;
    }

    private FetchResult fetchOnce(URI uri, URI publicUri, Map<String, String> headers,
                                  AtomicBoolean cancellation, Set<String> sensitiveQueryParameters,
                                  Consumer<FetchProgress> progress, int attempt, int maxAttempts) {
        long started = System.nanoTime();
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(properties.getRequestTimeout())
                .header("Accept", HTML_ACCEPT)
                .header("User-Agent", properties.getUserAgent());
        headers.forEach(builder::header);

        CompletableFuture<HttpResponse<byte[]>> future = httpClient.sendAsync(
                builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        try {
            HttpResponse<byte[]> response = await(future, cancellation, progress, attempt, maxAttempts, started);
            long elapsedMs = elapsedMs(started);
            if (response == null) {
                return cancelled(publicUri, elapsedMs);
            }
            byte[] bytes = response.body() == null ? new byte[0] : response.body();
            String contentType = response.headers().firstValue("content-type").orElse("");
            Map<String, String> importantHeaders = importantHeaders(response);
            if (bytes.length > properties.getMaxBodyBytes()) {
                return new FetchResult(publicUri, redactUri(response.uri(), sensitiveQueryParameters),
                        response.statusCode(), contentType, "",
                        elapsedMs, FetchCategory.INVALID_CONTENT_TYPE, false, importantHeaders);
            }
            String body = new String(bytes, StandardCharsets.UTF_8);
            FetchCategory category = classify(response.statusCode(), contentType, body);
            boolean retryable = switch (category) {
                case CHALLENGE_PAGE, RATE_LIMITED, SERVER_ERROR, NETWORK_ERROR -> true;
                default -> false;
            };
            log.atDebug().log("HTTP fetch {} {} -> {} in {} ms", response.statusCode(), safeUri(uri),
                    category, elapsedMs);
            return new FetchResult(publicUri, redactUri(response.uri(), sensitiveQueryParameters),
                    response.statusCode(), contentType, body,
                    elapsedMs, category, retryable, importantHeaders);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            if (cancellation != null) {
                cancellation.set(true);
            }
            return cancelled(publicUri, elapsedMs(started));
        } catch (ExecutionException | TimeoutException error) {
            future.cancel(true);
            long elapsedMs = elapsedMs(started);
            log.atWarn().log("HTTP fetch failed for {}: {}", safeUri(uri), error.getClass().getSimpleName());
            return new FetchResult(publicUri, publicUri, 0, "", "", elapsedMs,
                    FetchCategory.NETWORK_ERROR, true, Map.of());
        }
    }

    private HttpResponse<byte[]> await(CompletableFuture<HttpResponse<byte[]>> future,
                                       AtomicBoolean cancellation, Consumer<FetchProgress> progress,
                                       int attempt, int maxAttempts, long started)
            throws InterruptedException, ExecutionException, TimeoutException {
        long lastReportedAt = System.nanoTime();
        while (!future.isDone()) {
            if (isCancelled(cancellation)) {
                future.cancel(true);
                return null;
            }
            long now = System.nanoTime();
            if (TimeUnit.NANOSECONDS.toMillis(now - lastReportedAt) >= 5_000L) {
                long elapsed = elapsedMs(started);
                emit(progress, attempt, maxAttempts, elapsed, FetchProgressPhase.WAITING,
                        "来源请求仍在等待响应（已等待 " + formatSeconds(elapsed) + "）");
                lastReportedAt = now;
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
        // Cloudflare/Turnstile 等挑战页可能以 403 返回。先检查页面特征，避免把
        // 需要人工复核的来源误报成普通权限拒绝，也不允许调用方尝试绕过挑战。
        String normalizedBody = body == null ? "" : body.toLowerCase(Locale.ROOT);
        if (CHALLENGE_MARKERS.stream().anyMatch(normalizedBody::contains)) {
            return FetchCategory.CHALLENGE_PAGE;
        }
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
        return FetchCategory.SUCCESS;
    }

    private long retryDelayMs(FetchResult result, int attempt) {
        String retryAfter = result.importantHeaders().get("retry-after");
        Long headerDelay = parseRetryAfter(retryAfter);
        if (headerDelay != null) {
            return Math.min(headerDelay, Duration.ofMinutes(2).toMillis());
        }
        if (result.category() == FetchCategory.CHALLENGE_PAGE) {
            Duration configured = properties.getChallengeRetryDelay();
            long delay = configured == null ? Duration.ofSeconds(10).toMillis()
                    : Math.max(0L, configured.toMillis());
            return Math.min(delay, Duration.ofMinutes(2).toMillis());
        }
        long base = Math.max(0L, properties.getRetryBaseDelay().toMillis());
        return Math.min(base * (1L << Math.min(attempt - 1, 6)), Duration.ofSeconds(30).toMillis());
    }

    private static String retryMessage(FetchResult result, long delayMs) {
        if (result.category() == FetchCategory.CHALLENGE_PAGE) {
            return "来源正在完成访问验证，等待后重新检测（" + formatSeconds(delayMs) + "）";
        }
        return "来源响应异常，等待重试（" + formatSeconds(delayMs) + "）";
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
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER))
                .version(HttpClient.Version.HTTP_2);
        if (properties.isProxyEnabled()) {
            builder.proxy(ProxySelector.of(new InetSocketAddress(
                    properties.getProxyHost(), properties.getProxyPort())));
        }
        return builder.build();
    }

    private static boolean sleepCancellable(long delayMs, AtomicBoolean cancellation,
                                             Consumer<FetchProgress> progress, int attempt,
                                             int maxAttempts, long started,
                                             FetchProgressPhase phase, String message) {
        long remaining = delayMs;
        long lastReportedAt = System.nanoTime();
        while (remaining > 0) {
            if (isCancelled(cancellation)) {
                return false;
            }
            long now = System.nanoTime();
            if (TimeUnit.NANOSECONDS.toMillis(now - lastReportedAt) >= 5_000L) {
                emit(progress, attempt, maxAttempts, elapsedMs(started), phase, message);
                lastReportedAt = now;
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

    private static void emit(Consumer<FetchProgress> progress, int attempt, int maxAttempts,
                             long elapsedMs, FetchProgressPhase phase, String message) {
        try {
            progress.accept(new FetchProgress(attempt, maxAttempts, elapsedMs, phase, message));
        } catch (RuntimeException callbackFailure) {
            log.debug("Crawler request progress callback failed: {}",
                    callbackFailure.getClass().getSimpleName());
        }
    }

    private static String formatSeconds(long milliseconds) {
        return Math.max(1L, milliseconds / 1_000L) + " 秒";
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

    static URI redactUri(URI uri, Set<String> sensitiveQueryParameters) {
        if (uri == null || uri.getRawQuery() == null || sensitiveQueryParameters.isEmpty()) {
            return uri;
        }
        StringBuilder query = new StringBuilder();
        for (String pair : uri.getRawQuery().split("&")) {
            if (query.length() > 0) query.append('&');
            int separator = pair.indexOf('=');
            String key = separator >= 0 ? pair.substring(0, separator) : pair;
            query.append(key);
            if (separator >= 0) {
                query.append('=').append(sensitiveQueryParameters.contains(key)
                        ? "REDACTED" : pair.substring(separator + 1));
            }
        }
        try {
            return new URI(uri.getScheme(), uri.getRawAuthority(), uri.getRawPath(),
                    query.toString(), uri.getRawFragment());
        } catch (java.net.URISyntaxException impossible) {
            return URI.create(uri.getScheme() + "://" + uri.getAuthority() + uri.getPath());
        }
    }

    private static long elapsedMs(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }
}
