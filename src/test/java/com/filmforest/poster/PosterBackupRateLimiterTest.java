package com.filmforest.poster;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PosterBackupRateLimiterTest {

    @Test
    void defaultsMatchTheRequestedFiveSecondTenImageWindow() {
        PosterBackupProperties properties = new PosterBackupProperties();

        assertThat(properties.getRateLimitWindow()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.getMaxRequestsPerWindow()).isEqualTo(10);
    }

    @Test
    void blockedAcquisitionHonorsThreadInterruption() throws InterruptedException {
        PosterBackupRateLimiter limiter = new PosterBackupRateLimiter(Duration.ofHours(1), 1);
        limiter.acquire();
        Thread.currentThread().interrupt();

        assertThatThrownBy(limiter::acquire)
                .isInstanceOf(InterruptedException.class);
    }
}
