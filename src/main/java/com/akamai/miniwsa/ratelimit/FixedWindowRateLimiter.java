package com.akamai.miniwsa.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Fixed-window counter: at most {@code limit} acquires per key per window.
 * {@link #tryAcquire} returns empty when allowed, or seconds until the window
 * resets when denied.
 */
@Component
public class FixedWindowRateLimiter {

    private final int limit;
    private final long windowMillis;
    private final Clock clock;
    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    @Autowired
    public FixedWindowRateLimiter(
            @Value("${app.rate-limit.requests-per-minute:100}") int limit,
            Clock clock) {
        this(limit, Duration.ofMinutes(1), clock);
    }

    /** Test-friendly constructor with an explicit window length. */
    FixedWindowRateLimiter(int limit, Duration window, Clock clock) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be >= 1");
        }
        this.limit = limit;
        this.windowMillis = window.toMillis();
        this.clock = clock;
    }

    /**
     * @return empty if the request is allowed; otherwise seconds to wait (at least 1)
     */
    public OptionalLong tryAcquire(String key) {
        long now = clock.millis();
        long windowStart = now - (now % windowMillis);
        WindowCounter counter = counters.compute(key, (k, existing) -> {
            if (existing == null || existing.windowStart != windowStart) {
                return new WindowCounter(windowStart, new AtomicInteger(0));
            }
            return existing;
        });
        int count = counter.hits.incrementAndGet();
        if (count <= limit) {
            return OptionalLong.empty();
        }
        long retryAfterMs = windowStart + windowMillis - now;
        long retryAfterSeconds = Math.max(1L, (retryAfterMs + 999) / 1000);
        return OptionalLong.of(retryAfterSeconds);
    }

    private record WindowCounter(long windowStart, AtomicInteger hits) {
    }
}
