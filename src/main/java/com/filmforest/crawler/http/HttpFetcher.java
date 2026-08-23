package com.filmforest.crawler.http;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public interface HttpFetcher {

    /**
     * 只有实现了请求级观察回调的 fetcher 才会使用扩展重载；保留旧的四参数调用，
     * 让现有测试替身和其他来源适配器继续使用原有契约。
     */
    default boolean supportsProgressCallbacks() {
        return false;
    }

    enum FetchProgressPhase {
        WAITING,
        REQUESTING,
        RETRYING
    }

    /**
     * 可见的单次 HTTP 请求状态。elapsedMs 只表示当前请求/等待阶段已经经过的时间，
     * 不代表来源内容解析完成度；调用方可以用它刷新 Job 心跳并向管理端展示真实等待状态。
     */
    record FetchProgress(int attempt, int maxAttempts, long elapsedMs,
                         FetchProgressPhase phase, String message) {
    }

    default FetchResult fetch(URI uri, Map<String, String> headers, int rateLimitMs,
                              AtomicBoolean cancellation) {
        return fetch(uri, headers, rateLimitMs, cancellation, Set.of());
    }

    FetchResult fetch(URI uri, Map<String, String> headers, int rateLimitMs,
                      AtomicBoolean cancellation, Set<String> sensitiveQueryParameters);

    /**
     * 带请求内进度回调的兼容扩展；旧实现不支持回调时仍保持原有抓取语义。
     */
    default FetchResult fetch(URI uri, Map<String, String> headers, int rateLimitMs,
                              AtomicBoolean cancellation,
                              Consumer<FetchProgress> progress) {
        return fetch(uri, headers, rateLimitMs, cancellation, Set.of(), progress);
    }

    default FetchResult fetch(URI uri, Map<String, String> headers, int rateLimitMs,
                              AtomicBoolean cancellation, Set<String> sensitiveQueryParameters,
                              Consumer<FetchProgress> progress) {
        return fetch(uri, headers, rateLimitMs, cancellation, sensitiveQueryParameters);
    }
}
