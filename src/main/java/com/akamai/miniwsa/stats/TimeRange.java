package com.akamai.miniwsa.stats;

import java.time.Instant;

/** The [from, to] window a stats summary covers. */
public record TimeRange(
        Instant from,
        Instant to
) {
}
