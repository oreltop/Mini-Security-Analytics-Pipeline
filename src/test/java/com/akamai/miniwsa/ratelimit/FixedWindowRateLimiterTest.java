package com.akamai.miniwsa.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class FixedWindowRateLimiterTest {

    @Test
    void allowsUpToLimitThenDeniesWithinSameWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-20T12:00:00Z"));
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(3, Duration.ofMinutes(1), clock);

        assertThat(limiter.tryAcquire("1.1.1.1")).isEmpty();
        assertThat(limiter.tryAcquire("1.1.1.1")).isEmpty();
        assertThat(limiter.tryAcquire("1.1.1.1")).isEmpty();

        OptionalLong retryAfter = limiter.tryAcquire("1.1.1.1");
        assertThat(retryAfter).isPresent();
        assertThat(retryAfter.getAsLong()).isBetween(1L, 60L);
    }

    @Test
    void tracksKeysIndependently() {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-20T12:00:00Z"));
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(1, Duration.ofMinutes(1), clock);

        assertThat(limiter.tryAcquire("1.1.1.1")).isEmpty();
        assertThat(limiter.tryAcquire("1.1.1.1")).isPresent();
        assertThat(limiter.tryAcquire("2.2.2.2")).isEmpty();
    }

    @Test
    void resetsAfterWindowElapses() {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-20T12:00:00Z"));
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(1, Duration.ofMinutes(1), clock);

        assertThat(limiter.tryAcquire("1.1.1.1")).isEmpty();
        assertThat(limiter.tryAcquire("1.1.1.1")).isPresent();

        clock.advance(Duration.ofMinutes(1));
        assertThat(limiter.tryAcquire("1.1.1.1")).isEmpty();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
