package com.akamai.miniwsa.stats;

/** Per-path aggregate: how many events targeted this path. */
public record PathCount(
        String path,
        long count
) {
}
