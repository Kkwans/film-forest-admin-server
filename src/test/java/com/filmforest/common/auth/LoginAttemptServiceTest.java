package com.filmforest.common.auth;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceTest {

    @Test
    void blocksFifthFailureByAddressAndNormalizedUsername() {
        LoginAttemptService service = new LoginAttemptService(Clock.systemUTC());

        for (int i = 0; i < LoginAttemptService.MAX_FAILURES; i++) {
            service.recordFailure("192.0.2.10", " Admin ");
        }

        assertThat(service.isBlocked("192.0.2.10", "admin")).isTrue();
        assertThat(service.isBlocked("192.0.2.11", "admin")).isFalse();
        service.recordSuccess("192.0.2.10", "ADMIN");
        assertThat(service.isBlocked("192.0.2.10", "admin")).isFalse();
    }

    @Test
    void expiresFailuresAfterWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-08T00:00:00Z"));
        LoginAttemptService service = new LoginAttemptService(clock);
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES; i++) {
            service.recordFailure("192.0.2.10", "admin");
        }

        clock.advance(Duration.ofMinutes(15));

        assertThat(service.isBlocked("192.0.2.10", "admin")).isFalse();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
