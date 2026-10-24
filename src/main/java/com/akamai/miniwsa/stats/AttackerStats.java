package com.akamai.miniwsa.stats;

/** Per-attacker aggregate: event count and average threat score for a client IP. */
public record AttackerStats(
        String clientIp,
        long count,
        double avgThreatScore
) {
}
