package com.filmforest.poster;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;

/** 共享海报备份下载的滑动窗口限流器。 */
@Component
public class PosterBackupRateLimiter {

    private final long windowNanos;
    private final int maxRequests;
    private final Deque<Long> requestTimes = new ArrayDeque<>();

    public PosterBackupRateLimiter(PosterBackupProperties properties) {
        this(properties.getRateLimitWindow(), properties.getMaxRequestsPerWindow());
    }

    PosterBackupRateLimiter(Duration window, int maxRequests) {
        if (window == null || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("海报下载限流窗口必须大于 0");
        }
        if (maxRequests < 1) {
            throw new IllegalArgumentException("海报下载限流次数必须大于 0");
        }
        long windowNanos = window.toNanos();
        if (windowNanos <= 0) {
            throw new IllegalArgumentException("海报下载限流窗口过大或无效");
        }
        this.windowNanos = windowNanos;
        this.maxRequests = maxRequests;
    }

    /** 在获得一个下载配额前阻塞；中断会透传给调度线程。 */
    public synchronized void acquire() throws InterruptedException {
        for (;;) {
            long now = System.nanoTime();
            while (!requestTimes.isEmpty()
                    && now - requestTimes.peekFirst() >= windowNanos) {
                requestTimes.removeFirst();
            }
            if (requestTimes.size() < maxRequests) {
                requestTimes.addLast(now);
                return;
            }

            long waitNanos = windowNanos - (now - requestTimes.peekFirst());
            long waitMillis = TimeUnit.NANOSECONDS.toMillis(waitNanos);
            int waitNanosRemainder = (int) (waitNanos - TimeUnit.MILLISECONDS.toNanos(waitMillis));
            wait(Math.max(1L, waitMillis), waitNanosRemainder);
        }
    }
}
