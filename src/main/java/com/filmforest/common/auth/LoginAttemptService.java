package com.filmforest.common.auth;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginAttemptService {

    static final int MAX_FAILURES = 5;
    static final long WINDOW_MILLIS = Duration.ofMinutes(15).toMillis();
    static final int MAX_ENTRIES = 10_000;

    private final Clock clock;
    private final ConcurrentHashMap<String, AttemptWindow> attempts = new ConcurrentHashMap<>();

    public LoginAttemptService() {
        this(Clock.systemUTC());
    }

    LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    public boolean isBlocked(String remoteAddress, String username) {
        String key = key(remoteAddress, username);
        long now = clock.millis();
        AttemptWindow window = attempts.get(key);
        if (window == null) {
            return false;
        }
        if (isExpired(window, now)) {
            attempts.remove(key, window);
            return false;
        }
        return window.failures() >= MAX_FAILURES;
    }

    public void recordFailure(String remoteAddress, String username) {
        String key = key(remoteAddress, username);
        long now = clock.millis();
        ensureCapacity(key, now);
        attempts.compute(key, (ignored, current) -> {
            if (current == null || isExpired(current, now)) {
                return new AttemptWindow(1, now);
            }
            return new AttemptWindow(current.failures() + 1, current.windowStartedAt());
        });
    }

    public void recordSuccess(String remoteAddress, String username) {
        attempts.remove(key(remoteAddress, username));
    }

    private void ensureCapacity(String key, long now) {
        if (attempts.containsKey(key) || attempts.size() < MAX_ENTRIES) {
            return;
        }
        attempts.entrySet().removeIf(entry -> isExpired(entry.getValue(), now));
        if (attempts.size() < MAX_ENTRIES) {
            return;
        }
        attempts.entrySet().stream()
                .min(Comparator.comparingLong(entry -> entry.getValue().windowStartedAt()))
                .map(Map.Entry::getKey)
                .ifPresent(attempts::remove);
    }

    private boolean isExpired(AttemptWindow window, long now) {
        return now - window.windowStartedAt() >= WINDOW_MILLIS;
    }

    private String key(String remoteAddress, String username) {
        String address = remoteAddress == null ? "unknown" : remoteAddress.trim();
        String normalizedUsername = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        return address + '\u0000' + normalizedUsername;
    }

    private record AttemptWindow(int failures, long windowStartedAt) {
    }
}
