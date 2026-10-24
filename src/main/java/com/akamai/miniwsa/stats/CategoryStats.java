package com.akamai.miniwsa.stats;

/** Per-category aggregate: event count and average threat score. */
public record CategoryStats(
        long count,
        double avgThreatScore
) {
}
